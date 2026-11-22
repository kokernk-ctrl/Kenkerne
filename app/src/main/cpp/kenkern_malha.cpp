/**
 * kenkern_malha.cpp — MESHOPTIMIZER NATIVO (B6, v0.1.17): dedup de vértices,
 * otimização de cache e LODs por simplificação no libkenkern.so, sobre o
 * meshoptimizer (MIT, vendorizado em third_party/meshoptimizer).
 *
 * API JNI (usada por com.kenkern.engine.nativecore.MalhaNativa):
 *
 *  dedupEOtimizarNativo(vertices, indicesEntrada, contagemVertices, grupos,
 *                       remapSaida, indicesSaida)
 *    vertices:  ByteBuffer DIRETO com contagemVertices × 32 bytes — o MESMO
 *               registro que o dedup Kotlin compara (px,py,pz,nx,ny,nz,u,v),
 *               então a contagem de vértices únicos é IDÊNTICA à do Kotlin;
 *    grupos:    jintArray com pares (offset, count) dos grupos de material —
 *               a otimização de cache roda POR GRUPO (as faixas contíguas de
 *               índice dos materiais continuam válidas);
 *    remapSaida: ByteBuffer DIRETO contagemVertices × 4 (tabela old→new);
 *    indicesSaida: ByteBuffer DIRETO mesmo tamanho de indicesEntrada.
 *    Retorna a CONTAGEM DE VÉRTICES ÚNICOS ou código negativo de erro.
 *
 *  simplificarNativo(indicesEntrada, contagemIndices, posicoes,
 *                    contagemVertices, alvoIndices, erroSaida, indicesSaida)
 *    posicoes:  ByteBuffer DIRETO contagemVertices × 12 (float xyz).
 *    erroSaida: jfloatArray[1] — o ERRO DE SIMPLIFICAÇÃO reportado pelo
 *               meshopt_simplify (B6: "erro de simplificação reportado").
 *    Retorna a contagem de índices do LOD ou código negativo.
 *
 * VALIDAÇÃO DE TODOS OS LIMITES (nada de leitura fora do buffer, nada de
 * alocação ilimitada): dimensões > 0, múltiplos de 3 nos índices, buffers
 * diretos com capacidade conferida, teto de 64 M vértices/200 M índices.
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

#include "meshoptimizer/meshoptimizer.h"

#define TAG "Kenkern/MalhaNativa"

namespace {

// Limites duros (B6: "validação de todos os limites").
constexpr int64_t LIMITE_VERTICES = 64LL * 1024 * 1024;   // 64 M vértices
constexpr int64_t LIMITE_INDICES = 200LL * 1024 * 1024;   // 200 M índices

inline bool capacidadeOk(JNIEnv* env, jobject buf, int64_t esperado) {
    return env->GetDirectBufferCapacity(buf) >= esperado;
}

} // namespace

extern "C" {

/**
 * DEDUP (meshopt_generateVertexRemap + remapIndexBuffer) + OTIMIZAÇÃO DE
 * CACHE POR GRUPO (meshopt_optimizeVertexCache). A ordem dos índices é
 * preservada pelo remap (os grupos de material continuam apontando para as
 * mesmas faixas); dentro de cada grupo os triângulos são reordenados para o
 * cache de vértices da GPU.
 */
JNIEXPORT jint JNICALL
Java_com_kenkern_engine_nativecore_MalhaNativa_dedupEOtimizarNativo(
        JNIEnv* env, jobject /*thiz*/, jobject jVertices, jobject jIndices,
        jint contagemVertices, jintArray jGrupos, jobject jRemap, jobject jIndicesSaida) {
    if (jVertices == nullptr || jIndices == nullptr || jRemap == nullptr || jIndicesSaida == nullptr || jGrupos == nullptr) return -1;
    if (contagemVertices <= 0) return -2;
    if (contagemVertices > LIMITE_VERTICES) return -3;

    const int64_t bytesVertices = static_cast<int64_t>(contagemVertices) * 32;
    auto* vertices = static_cast<const uint8_t*>(env->GetDirectBufferAddress(jVertices));
    auto* indices = static_cast<const uint32_t*>(env->GetDirectBufferAddress(jIndices));
    auto* remap = static_cast<uint32_t*>(env->GetDirectBufferAddress(jRemap));
    auto* saida = static_cast<uint32_t*>(env->GetDirectBufferAddress(jIndicesSaida));
    if (vertices == nullptr || indices == nullptr || remap == nullptr || saida == nullptr) return -4;

    const int64_t capacidadeIndices = env->GetDirectBufferCapacity(jIndices);
    const int64_t capacidadeSaida = env->GetDirectBufferCapacity(jIndicesSaida);
    if (capacidadeIndices < 4 || capacidadeSaida < capacidadeIndices) return -5;
    if (!capacidadeOk(env, jVertices, bytesVertices)) return -6;
    if (!capacidadeOk(env, jRemap, static_cast<int64_t>(contagemVertices) * 4)) return -7;
    if (capacidadeIndices > LIMITE_INDICES * 4) return -8;
    const int contagemIndices = static_cast<int>(capacidadeIndices / 4);
    if (contagemIndices % 3 != 0) return -9; // triângulos

    // ---------- DEDUP: remap por identidade binária do registro de 32 bytes
    const size_t unicos = meshopt_generateVertexRemap(
        remap, indices, static_cast<size_t>(contagemIndices),
        vertices, static_cast<size_t>(contagemVertices), 32
    );
    meshopt_remapIndexBuffer(saida, indices, static_cast<size_t>(contagemIndices), remap);

    // ---------- CACHE: POR GRUPO de material (faixas contíguas preservadas)
    jsize nGrupos = env->GetArrayLength(jGrupos);
    if (nGrupos % 2 != 0) return -10;
    jint* grupos = env->GetIntArrayElements(jGrupos, nullptr);
    if (grupos == nullptr) return -11;
    // Buffer de trabalho por grupo (destination não pode sobrepor a origem).
    uint32_t* trabalho = static_cast<uint32_t*>(malloc(static_cast<size_t>(contagemIndices) * 4));
    if (trabalho == nullptr) {
        env->ReleaseIntArrayElements(jGrupos, grupos, JNI_ABORT);
        LOGW("sem memória para o buffer de trabalho de %d índices", contagemIndices);
        return -12;
    }
    for (jsize g = 0; g + 1 < nGrupos; g += 2) {
        const jint ini = grupos[g];
        const jint qtde = grupos[g + 1];
        if (ini < 0 || qtde < 0 || qtde % 3 != 0 || static_cast<int64_t>(ini) + qtde > contagemIndices) {
            free(trabalho);
            env->ReleaseIntArrayElements(jGrupos, grupos, JNI_ABORT);
            return -13; // grupo fora do buffer
        }
        if (qtde == 0) continue;
        meshopt_optimizeVertexCache(
            trabalho, saida + ini, static_cast<size_t>(qtde), static_cast<size_t>(unicos)
        );
        memcpy(saida + ini, trabalho, static_cast<size_t>(qtde) * 4);
    }
    free(trabalho);
    env->ReleaseIntArrayElements(jGrupos, grupos, JNI_ABORT);
    return static_cast<jint>(unicos);
}

/**
 * LOD POR SIMPLIFICAÇÃO (meshopt_simplify). Retorna a contagem de índices
 * do LOD (≤ alvo, múltipla de 3) e escreve o ERRO de simplificação em
 * erroSaida[0] — o relatório do PosProcessamento mostra o número.
 */
JNIEXPORT jint JNICALL
Java_com_kenkern_engine_nativecore_MalhaNativa_simplificarNativo(
        JNIEnv* env, jobject /*thiz*/, jobject jIndices, jint contagemIndices,
        jobject jPosicoes, jint contagemVertices, jint alvoIndices,
        jfloatArray jErroSaida, jobject jDestino) {
    if (jIndices == nullptr || jPosicoes == nullptr || jErroSaida == nullptr || jDestino == nullptr) return -1;
    if (contagemVertices <= 0 || contagemVertices > LIMITE_VERTICES) return -2;
    if (contagemIndices <= 0 || contagemIndices % 3 != 0 || contagemIndices > LIMITE_INDICES) return -3;
    if (alvoIndices <= 0 || alvoIndices % 3 != 0) return -4;

    auto* indices = static_cast<const uint32_t*>(env->GetDirectBufferAddress(jIndices));
    auto* posicoes = static_cast<const float*>(env->GetDirectBufferAddress(jPosicoes));
    auto* destino = static_cast<uint32_t*>(env->GetDirectBufferAddress(jDestino));
    if (indices == nullptr || posicoes == nullptr || destino == nullptr) return -5;
    if (!capacidadeOk(env, jIndices, static_cast<int64_t>(contagemIndices) * 4)) return -6;
    if (!capacidadeOk(env, jPosicoes, static_cast<int64_t>(contagemVertices) * 12)) return -7;
    if (!capacidadeOk(env, jDestino, static_cast<int64_t>(contagemIndices) * 4)) return -8;

    float erro = 0.0f;
    const size_t resultado = meshopt_simplify(
        destino, indices, static_cast<size_t>(contagemIndices),
        posicoes, static_cast<size_t>(contagemVertices), sizeof(float) * 3,
        static_cast<size_t>(alvoIndices), 0.05f /* erro alvo relativo */,
        0 /* opções */, &erro
    );
    if (env->GetArrayLength(jErroSaida) < 1) return -9;
    env->SetFloatArrayRegion(jErroSaida, 0, 1, &erro);
    return static_cast<jint>(resultado);
}

} // extern "C"
