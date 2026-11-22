/**
 * kenkern_native.cpp — NÚCLEO NATIVO C++17 da KENKERN (B3, v0.1.17).
 *
 * Decisão do ROADMAP (não reabrir): C++17 via NDK/CMake; Rust foi descartado
 * porque as bibliotecas prontas (etcpak, meshoptimizer) são C/C++. Somente
 * arm64-v8a por enquanto.
 *
 * Este arquivo é a BASE do núcleo: handler de sinais + eco de validação +
 * versão. Módulos pesados (ETC2 em B4, meshoptimizer em B6) entram por ARQUIVOS
 * PRÓPRIOS neste mesmo alvo CMake. Todo módulo nativo tem FALLBACK Kotlin
 * automático — a biblioteca ausente NUNCA derruba o app.
 *
 * HANDLER DE SINAIS (B3c): SIGSEGV, SIGABRT, SIGBUS e SIGFPE são capturados e
 * gravam crash_logs/native_crash.txt com sinal, endereço e backtrace ANTES de
 * o processo morrer (o handler Kotlin de exceções não vê crash nativo). O
 * diálogo de crash do Hub passa a mostrar esse arquivo também.
 *
 * ASYNC-SIGNAL-SAFE: dentro do handler só usamos write()/open()/close() e
 * formatadores manuais (sem malloc/printf). O _Unwind_Backtrace não é
 * formalmente async-signal-safe, mas é o mecanismo padrão dos crash handlers
 * do Android; a re-entrada é travada por g_escrevendo (crash dentro do
 * handler volta ao comportamento padrão imediatamente).
 */

#include <jni.h>
#include <android/log.h>

#include <csignal>
#include <cstring>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <unwind.h>

#define KENKERN_NATIVE_VERSAO "1.0.0"
#define TAG "Kenkern/NativeCore"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace {

// Sinais cobertos (B3c) — em ordem fixa, com nome para o relatório.
const int SINAIS[] = {SIGSEGV, SIGABRT, SIGBUS, SIGFPE};
const char* NOME_SINAIS[] = {"SIGSEGV", "SIGABRT", "SIGBUS", "SIGFPE"};
const int NUM_SINAIS = 4;
struct sigaction g_acoesAntigas[NUM_SINAIS];

// Caminho completo de crash_logs/native_crash.txt (setado no install; lido
// apenas com funções signal-safe). 512 bytes cobre qualquer filesDir do Android.
char g_caminhoCrash[512] = {0};
volatile sig_atomic_t g_handlerInstalado = 0;
volatile sig_atomic_t g_escrevendo = 0;

// ------------------------------------------------- formatadores signal-safe

/** Escreve a string com write() (sem printf — async-signal-safe). */
inline void escreverStr(int fd, const char* s, size_t n) {
    // write() em loop: sinal pode chegar parcial; ignoramos o retorno — no
    // pior caso o arquivo fica truncado, que ainda é melhor que nada.
    while (n > 0) {
        ssize_t w = write(fd, s, n);
        if (w <= 0) break;
        s += w;
        n -= static_cast<size_t>(w);
    }
}

inline void escreverStr(int fd, const char* s) { escreverStr(fd, s, strlen(s)); }

/** Decimal manual (sem snprintf). */
void decPara(char* buf, long v) {
    char tmp[24];
    int i = 0;
    unsigned long u = v < 0 ? 0UL : static_cast<unsigned long>(v);
    if (u == 0) { buf[0] = '0'; buf[1] = '\0'; return; }
    while (u > 0 && i < 23) { tmp[i++] = static_cast<char>('0' + (u % 10)); u /= 10; }
    int n = 0;
    while (i > 0) buf[n++] = tmp[--i];
    buf[n] = '\0';
}

/** Hexadecimal manual (sem snprintf). */
void hexPara(char* buf, uintptr_t v) {
    const char* digitos = "0123456789abcdef";
    char tmp[32];
    int i = 0;
    if (v == 0) { buf[0] = '0'; buf[1] = '\0'; return; }
    while (v > 0 && i < 31) { tmp[i++] = digitos[v & 0xF]; v >>= 4; }
    int n = 0;
    while (i > 0) buf[n++] = tmp[--i];
    buf[n] = '\0';
}

// ------------------------------------------------- backtrace (unwind)

struct EstadoUnwind {
    uintptr_t quadros[32];
    int n;
};

_Unwind_Reason_Code unwindCallback(struct _Unwind_Context* contexto, void* dado) {
    auto* estado = static_cast<EstadoUnwind*>(dado);
    if (estado->n >= 32) return _URC_END_OF_STACK;
    estado->quadros[estado->n++] = reinterpret_cast<uintptr_t>(_Unwind_GetIP(contexto));
    return _URC_NO_REASON;
}

// ------------------------------------------------- handler de sinal

void handlerSinal(int sinal, siginfo_t* info, void* /*ucontext*/) {
    // Re-entrada (crash dentro do handler): volta ao padrão IMEDIATAMENTE.
    if (g_escrevendo) {
        signal(sinal, SIG_DFL);
        raise(sinal);
        return;
    }
    g_escrevendo = 1;

    if (g_caminhoCrash[0] != '\0') {
        int fd = open(g_caminhoCrash, O_WRONLY | O_CREAT | O_TRUNC, 0600);
        if (fd >= 0) {
            char buf[64];
            escreverStr(fd, "NATIVE CRASH — núcleo C++ da KENKERN\n");
            escreverStr(fd, "sinal: ");
            const char* nome = "DESCONHECIDO";
            for (int i = 0; i < NUM_SINAIS; i++) {
                if (SINAIS[i] == sinal) nome = NOME_SINAIS[i];
            }
            escreverStr(fd, nome);
            escreverStr(fd, " (");
            decPara(buf, sinal);
            escreverStr(fd, buf);
            escreverStr(fd, ")\n");
            escreverStr(fd, "endereço: 0x");
            hexPara(buf, reinterpret_cast<uintptr_t>(info != nullptr ? info->si_addr : nullptr));
            escreverStr(fd, buf);
            escreverStr(fd, "\n");
            escreverStr(fd, "versão nativa: ");
            escreverStr(fd, KENKERN_NATIVE_VERSAO);
            escreverStr(fd, "\n");

            EstadoUnwind estado;
            estado.n = 0;
            _Unwind_Backtrace(unwindCallback, &estado);
            escreverStr(fd, "backtrace: ");
            decPara(buf, estado.n);
            escreverStr(fd, buf);
            escreverStr(fd, " quadros\n");
            for (int i = 0; i < estado.n; i++) {
                escreverStr(fd, "#");
                decPara(buf, i);
                escreverStr(fd, buf);
                escreverStr(fd, " 0x");
                hexPara(buf, estado.quadros[i]);
                escreverStr(fd, buf);
                escreverStr(fd, "\n");
            }
            escreverStr(fd, "---\nO app fechou por um erro nativo. Envie este arquivo junto com o relatório de crash.\n");
            close(fd);
        }
    }

    // Processo morre com o comportamento PADRÃO do sinal: o Android gera o
    // tombstone do sistema e o Hub mostra o native_crash.txt no próximo início.
    signal(sinal, SIG_DFL);
    raise(sinal);
}

} // namespace

// ================================================================ API JNI

extern "C" {

/**
 * Eco de validação (B3d): devolve valor*2 + 1. A checagem "nativo" do
 * Diagnóstico usa eco(21) == 43 para provar que a chamada JNI funciona.
 */
JNIEXPORT jint JNICALL
Java_com_kenkern_engine_nativecore_NativeCore_ecoNativo(JNIEnv* /*env*/, jobject /*thiz*/, jint valor) {
    return valor * 2 + 1;
}

/** Versão do núcleo nativo (independente do versionName do app). */
JNIEXPORT jstring JNICALL
Java_com_kenkern_engine_nativecore_NativeCore_versaoNativa(JNIEnv* env, jobject /*thiz*/) {
    return env->NewStringUTF(KENKERN_NATIVE_VERSAO);
}

/**
 * Instala o handler dos 4 sinais (B3c). [caminhoArquivo] é o caminho COMPLETO
 * de crash_logs/native_crash.txt (o Kotlin garante que a pasta existe).
 * Retorna false se o caminho não couber no buffer ou algum sigaction falhar
 * (o app segue SEM handler nativo — nada derruba o boot).
 */
JNIEXPORT jboolean JNICALL
Java_com_kenkern_engine_nativecore_NativeCore_instalarHandlerSinais(JNIEnv* env, jobject /*thiz*/, jstring caminhoArquivo) {
    const char* c = env->GetStringUTFChars(caminhoArquivo, nullptr);
    if (c == nullptr) return JNI_FALSE;
    size_t n = strlen(c);
    if (n == 0 || n >= sizeof(g_caminhoCrash)) {
        env->ReleaseStringUTFChars(caminhoArquivo, c);
        LOGW("caminho do native_crash.txt inválido (%zu bytes)", n);
        return JNI_FALSE;
    }
    memcpy(g_caminhoCrash, c, n + 1);
    env->ReleaseStringUTFChars(caminhoArquivo, c);

    struct sigaction acao;
    memset(&acao, 0, sizeof(acao));
    acao.sa_sigaction = handlerSinal;
    acao.sa_flags = SA_SIGINFO;
    sigemptyset(&acao.sa_mask);

    int ok = 1;
    for (int i = 0; i < NUM_SINAIS; i++) {
        if (sigaction(SINAIS[i], &acao, &g_acoesAntigas[i]) != 0) {
            ok = 0;
            LOGW("sigaction falhou para %s (errno já no logcat)", NOME_SINAIS[i]);
        }
    }
    g_handlerInstalado = ok ? 1 : 0;
    return ok ? JNI_TRUE : JNI_FALSE;
}

/** True quando os 4 handlers estão instalados (checagem do Diagnóstico). */
JNIEXPORT jboolean JNICALL
Java_com_kenkern_engine_nativecore_NativeCore_handlerInstalado(JNIEnv* /*env*/, jobject /*thiz*/) {
    return g_handlerInstalado ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
