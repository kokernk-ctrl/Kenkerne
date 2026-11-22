/**
 * kenkern_etc2.cpp — ENCODER ETC2 NATIVO (B4, v0.1.17): integra o etcpak
 * (BSD-3-Clause, vendorizado em third_party/etcpak) ao libkenkern.so.
 *
 * API JNI (usada por com.kenkern.engine.nativecore.Etc2Nativo):
 *   codificarNativo(rgba: ByteBuffer DIRETO, saida: ByteBuffer DIRETO,
 *                   largura, altura, temAlfa, qualidadeRapida): Int
 *   → devolve o número de bytes escritos em [saida] ou um CÓDIGO NEGATIVO de
 *     erro (o Kotlin cai no fallback com o motivo no Console).
 *
 * VALIDAÇÃO DE TODOS OS TAMANHOS E LIMITES (nada de leitura fora do buffer,
 * nada de alocação ilimitada):
 *   -1 buffer nulo · -2 buffer não-direto · -3 dimensões inválidas (≤ 0)
 *   -4 textura acima do limite de 256 megapixels
 *   -5 buffer de entrada menor que largura×altura×4
 *   -6 buffer de saída menor que blocos×(8 ou 16) bytes
 *   -7 sem memória para o padding de borda
 *
 * PADDING DE BORDA: o etcpak exige largura/altura MÚLTIPLAS DE 4 (a iteração
 * de blocos usa width/4 inteiro). Para dimensões não múltiplas de 4, o wrapper
 * replica a última linha/coluna num buffer temporário COM LIMITE (512 MB) — a
 * contagem de blocos sai idêntica ao encoder Kotlin (ceil/4), que é o que o
 * glCompressedTexImage2D espera.
 *
 * MULTITHREAD: faixas de LINHAS de blocos divididas em N−1 threads
 * (hardware_concurrency − 1, teto 8 — o mesmo N do encoder Kotlin). O etcpak
 * itera blocos linha a linha internamente; cada faixa recebe o ponteiro de
 * origem na primeira linha de pixels da faixa e o destino na primeira bloco-
 * linha da faixa — regiões DISJUNTAS, sem trava.
 */

#include <jni.h>

#ifdef __ANDROID__
#  include <android/log.h>
#  define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#else
// HOST (testes JVM): mesma fonte, g++ — o motivo do erro volta pelo código retornado.
#  define LOGW(...) do { } while (0)
#endif

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <thread>
#include <vector>

#include "etcpak/ProcessRGB.hpp"

#define TAG "Kenkern/Etc2Nativo"

namespace {

// Limites duros (B4: "nada de alocação ilimitada").
constexpr int64_t LIMITE_MEGAPIXELS = 256LL * 1024 * 1024;   // 256 Mpx
constexpr int64_t LIMITE_BYTES_PAD = 512LL * 1024 * 1024;    // 512 MB de trabalho

/**
 * Converte a entrada [R,G,B,A] para a ordem que o etcpak espera — [B,G,R,A]
 * (o upstream lê PNG com png_set_bgr para os codecs ETC; os caminhos escalar
 * e SIMD do ProcessRGB_ETC2 leem byte0=B, byte2=R) — e ao MESMO tempo replica
 * a borda direita/inferior até múltiplo de 4 (a iteração de blocos do etcpak
 * usa width/4 inteiro). Um único passe, buffer com limite checado.
 */
uint32_t* converterBgraComPadding(const uint8_t* src, uint32_t largura, uint32_t altura, uint32_t w4, uint32_t h4) {
    const size_t bytes = static_cast<size_t>(w4) * h4 * sizeof(uint32_t);
    if (bytes > static_cast<size_t>(LIMITE_BYTES_PAD)) return nullptr;
    auto* out = static_cast<uint32_t*>(malloc(bytes));
    if (out == nullptr) return nullptr;
    for (uint32_t y = 0; y < h4; y++) {
        const uint32_t sy = y < altura ? y : altura - 1;
        const uint8_t* linha = src + static_cast<size_t>(sy) * largura * 4;
        uint32_t* destino = out + static_cast<size_t>(y) * w4;
        for (uint32_t x = 0; x < largura; x++) {
            const uint8_t* p = linha + static_cast<size_t>(x) * 4;
            // [R,G,B,A] → uint32 (little-endian: memória [B,G,R,A])
            destino[x] = static_cast<uint32_t>(p[0]) << 16 |
                         static_cast<uint32_t>(p[1]) << 8 |
                         static_cast<uint32_t>(p[2]) |
                         static_cast<uint32_t>(p[3]) << 24;
        }
        for (uint32_t x = largura; x < w4; x++) {
            destino[x] = destino[largura - 1];
        }
    }
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_com_kenkern_engine_nativecore_Etc2Nativo_codificarNativo(
        JNIEnv* env, jobject /*thiz*/, jobject jEntrada, jobject jSaida,
        jint largura, jint altura, jboolean temAlfa, jboolean qualidadeRapida) {
    if (jEntrada == nullptr || jSaida == nullptr) return -1;
    auto* srcBytes = static_cast<uint8_t*>(env->GetDirectBufferAddress(jEntrada));
    auto* dstBytes = static_cast<uint8_t*>(env->GetDirectBufferAddress(jSaida));
    if (srcBytes == nullptr || dstBytes == nullptr) return -2;
    if (largura <= 0 || altura <= 0) return -3;
    if (static_cast<int64_t>(largura) * static_cast<int64_t>(altura) > LIMITE_MEGAPIXELS) return -4;

    int64_t esperadoEntrada = static_cast<int64_t>(largura) * altura * 4;
    if (env->GetDirectBufferCapacity(jEntrada) < esperadoEntrada) return -5;

    const uint32_t blocosX = (static_cast<uint32_t>(largura) + 3) / 4;
    const uint32_t blocosY = (static_cast<uint32_t>(altura) + 3) / 4;
    const size_t bytesPorBloco = temAlfa == JNI_TRUE ? 16 : 8;
    const int64_t esperadoSaida = static_cast<int64_t>(blocosX) * blocosY * static_cast<int64_t>(bytesPorBloco);
    if (env->GetDirectBufferCapacity(jSaida) < esperadoSaida) return -6;

    // Conversão [R,G,B,A]→[B,G,R,A] + padding até múltiplo de 4 num único
    // passe (o etcpak exige BGRA e largura/altura múltiplas de 4).
    const uint32_t w4 = (static_cast<uint32_t>(largura) + 3) / 4 * 4;
    const uint32_t h4 = (static_cast<uint32_t>(altura) + 3) / 4 * 4;
    uint32_t* trabalho = converterBgraComPadding(srcBytes, static_cast<uint32_t>(largura), static_cast<uint32_t>(altura), w4, h4);
    if (trabalho == nullptr) {
        LOGW("buffer de trabalho falhou (textura %dx%d acima do limite de memória)", largura, altura);
        return -7;
    }
    const uint32_t* src = trabalho;
    const uint32_t larguraEnc = w4;

    auto* dst = reinterpret_cast<uint64_t*>(dstBytes);
    const bool heuristica = qualidadeRapida == JNI_TRUE; // true = rápido; false = busca exaustiva
    const uint32_t blocosPorLinha = blocosX;
    const uint32_t linhasTotais = blocosY;

    // MULTITHREAD: N−1 threads (mesmo N do encoder Kotlin), faixas de LINHAS.
    unsigned n = std::thread::hardware_concurrency();
    n = n > 1 ? n - 1 : 1;
    if (n > 8) n = 8;
    const uint32_t minimoPorFaixa = 4;

    if (n <= 1 || linhasTotais < minimoPorFaixa * 2) {
        if (temAlfa == JNI_TRUE) {
            CompressEtc2Rgba(src, dst, blocosX * linhasTotais, larguraEnc, heuristica);
        } else {
            CompressEtc2Rgb(src, dst, blocosX * linhasTotais, larguraEnc, heuristica);
        }
    } else {
        std::vector<std::thread> pool;
        pool.reserve(n);
        const uint32_t porFaixa = (linhasTotais + n - 1) / n;
        for (uint32_t f = 0; f < n; f++) {
            const uint32_t linhaIni = f * porFaixa;
            if (linhaIni >= linhasTotais) break;
            uint32_t linhaFim = linhaIni + porFaixa;
            if (linhaFim > linhasTotais) linhaFim = linhasTotais;
            const uint32_t blocosFaixa = blocosPorLinha * (linhaFim - linhaIni);
            // Primeiro PIXEL da faixa: linha de pixels linhaIni*4, coluna 0.
            const uint32_t* s = src + static_cast<size_t>(linhaIni) * 4 * larguraEnc;
            // Primeiro BLOCO da faixa (o layout de saída é linha-major).
            uint64_t* d = dst + static_cast<size_t>(linhaIni) * blocosPorLinha * (bytesPorBloco / 8);
            const bool alfa = temAlfa == JNI_TRUE;
            pool.emplace_back([=]() {
                if (alfa) {
                    CompressEtc2Rgba(s, d, blocosFaixa, larguraEnc, heuristica);
                } else {
                    CompressEtc2Rgb(s, d, blocosFaixa, larguraEnc, heuristica);
                }
            });
        }
        for (auto& t : pool) t.join();
    }

    free(trabalho);
    return static_cast<jint>(esperadoSaida);
}

} // extern "C"
