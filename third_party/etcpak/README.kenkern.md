# etcpak (vendorizado na KENKERN)

Fonte: https://github.com/wolfpld/etcpak
Commit vendorizado: 3d716e1550023dc5ff8b602636af8b76584e0b66 (2026-09-13)
Licença: BSD-3-Clause (LICENSE.txt) — registrada em /LICENSES.md

## Por que vendorizado
O B4 do ROADMAP (v0.1.17) exige o encoder ETC2 nativo mais rápido disponível
em C/C++; etcpak é a referência (SIMD SSE/NEON, BSD-3-Clause). Nenhuma
distribuição Maven/Gradle existe — vendorizar é a forma padrão.

## Arquivos incluídos (subconjunto mínimo para ETC2 RGB/RGBA)
- ProcessRGB.cpp/.hpp — CompressEtc2Rgb / CompressEtc2Rgba (o encoder)
- ProcessCommon.hpp, Tables.cpp/.hpp, Vector.hpp, Math.hpp, ForceInline.hpp,
  Dither.hpp (dependências diretas do ProcessRGB.cpp)
- LICENSE.txt, AUTHORS.txt — OBRIGATÓRIOS pela licença (BSD-3-Clause)

O restante do upstream (ferramentas de linha de comando, BC7/DXTc, mmap,
TaskDispatch) NÃO entra: a KENKERN tem o próprio despacho multithread no
wrapper JNI (app/src/main/cpp/kenkern_etc2.cpp).

## Alterações locais
NENHUMA nos arquivos acima (aplicação de patches precisa ser registrada aqui).
