# Núcleo nativo C++17 da KENKERN (B3, v0.1.17).
#
# Regras do ROADMAP: C++17 via NDK/CMake (decisão B3, não reabrir), somente
# arm64-v8a por enquanto (abiFilters no build.gradle.kts). Versões fixadas no
# Gradle e no CI: NDK 27.3.13750724 · CMake 3.22.1.
#
# B4 (v0.1.17): encoder ETC2 NATIVO — etcpak (BSD-3-Clause) vendorizado em
# third_party/etcpak (licença registrada em /LICENSES.md), subconjunto mínimo
# do caminho ETC2, SEM alterações locais.
cmake_minimum_required(VERSION 3.22.1)
project(kenkern CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# third_party/ fica na RAIZ do repositório (4 níveis acima de app/src/main/cpp).
get_filename_component(KENKERN_RAIZ "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
set(ETCPAK_DIR "${KENKERN_RAIZ}/third_party/etcpak")
set(MESHOPT_DIR "${KENKERN_RAIZ}/third_party/meshoptimizer")

if(NOT EXISTS "${ETCPAK_DIR}/ProcessRGB.cpp")
    message(FATAL_ERROR "etcpak não encontrado em ${ETCPAK_DIR} — clone o repositório completo (third_party/ é parte da fonte)")
endif()
if(NOT EXISTS "${MESHOPT_DIR}/simplifier.cpp")
    message(FATAL_ERROR "meshoptimizer não encontrado em ${MESHOPT_DIR} — clone o repositório completo (third_party/ é parte da fonte)")
endif()

add_library(kenkern SHARED
    kenkern_native.cpp
    kenkern_etc2.cpp
    kenkern_malha.cpp
    ${ETCPAK_DIR}/ProcessRGB.cpp
    ${ETCPAK_DIR}/Tables.cpp
    ${MESHOPT_DIR}/indexgenerator.cpp
    ${MESHOPT_DIR}/vcacheoptimizer.cpp
    ${MESHOPT_DIR}/simplifier.cpp
)

target_include_directories(kenkern PRIVATE
    ${KENKERN_RAIZ}/third_party
)

# NEON ligado por padrão no arm64 do NDK (__ARM_NEON) — caminho SIMD do etcpak.
# android: JNI helpers (libandroid) · log: __android_log_print (liblog).
# O unwinder (_Unwind_Backtrace) mora na libc — sem link extra.
target_link_libraries(kenkern android log)

# O NDK r27 fixado ainda usa segmentos ELF de 4 KiB por padrão. Suportar
# usuários Android com páginas de 16 KiB sem exigir atualizar a toolchain.
target_link_options(kenkern PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
