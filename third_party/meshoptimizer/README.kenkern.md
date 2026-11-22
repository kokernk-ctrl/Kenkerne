# meshoptimizer (vendorizado na KENKERN)

Fonte: https://github.com/zeux/meshoptimizer
Commit vendorizado: 8547fc321b782a2235dfc14c65a346a640544cd8 (2026-11-21)
Licença: MIT (LICENSE.md) — registrada em /LICENSES.md

## Por que vendorizado
B6 do ROADMAP (v0.1.17): dedup de vértices (meshopt_generateVertexRemap),
otimização de cache de vértices (meshopt_optimizeVertexCache) e LODs por
simplificação (meshopt_simplify) no núcleo C++ — biblioteca de referência MIT.

## Arquivos incluídos (subconjunto mínimo)
- meshoptimizer.h — API C (a parte usada; os templates C++ inline não são usados)
- indexgenerator.cpp — generateVertexRemap + remapIndexBuffer/remapVertexBuffer
- vcacheoptimizer.cpp — optimizeVertexCache
- simplifier.cpp — simplify (com erro de simplificação resultante)
- LICENSE.md — obrigatória (MIT)

O restante do upstream (compressão de vértices, meshlets, stripifier, tools)
NÃO entra: o wrapper JNI (app/src/main/cpp/kenkern_malha.cpp) só usa os 3
módulos acima.

## Alterações locais
NENHUMA (aplicação de patches precisa ser registrada aqui).
