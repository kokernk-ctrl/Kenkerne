# Núcleo nativo C++17 da KENKERN (B3, v0.1.17).
#
# Regras do ROADMAP: C++17 via NDK/CMake (decisão B3, não reabrir), somente
# arm64-v8a por enquanto (abiFilters no build.gradle.kts). Versões fixadas no
# Gradle e no CI: NDK 27.3.13750724 · CMake 3.22.1.
cmake_minimum_required(VERSION 3.22.1)
project(kenkern CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(kenkern SHARED
    kenkern_native.cpp
)

# android: JNI helpers (libandroid) · log: __android_log_print (liblog).
# O unwinder (_Unwind_Backtrace) mora na libc — sem link extra.
target_link_libraries(kenkern android log)
