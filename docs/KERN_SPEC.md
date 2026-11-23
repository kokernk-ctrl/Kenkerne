# KERN — Formato de malha 3D da KENKERN (versões 1 e 2)

KERN é o formato leve de malha 3D da engine KENKERN, desenhado para mobile:
vértices quantizados, índices 16/32 bits conforme o tamanho, tabela de LODs e
campos já reservados para SKINNING/ESQUELETO e para o futuro bloco de CLUSTERS
(meshlets) usado pela KENA (geometria virtualizada).

## Convenções

- Endianness: **little-endian** em todos os campos.
- Unidades: metros. Eixo Y para cima, destro.
- Coordenadas de posição quantizadas referem-se à bounding box do cabeçalho.

## Cabeçalho (80 bytes fixos)

| Offset | Tipo     | Campo             | Descrição                                            |
|--------|----------|-------------------|------------------------------------------------------|
| 0x00   | char[4]  | magic             | `"KERN"`                                             |
| 0x04   | u16      | version           | `2` na escrita; leitura aceita `1` e `2`                                       |
| 0x06   | u16      | flags             | bit 0 = clusters; bit 1 = tabela de materiais (v2)                     |
| 0x08   | f32[3]   | boundsMin         | mínimo da bounding box (x, y, z)                     |
| 0x14   | f32[3]   | boundsMax         | máximo da bounding box (x, y, z)                     |
| 0x20   | u32      | vertexCount       | número de vértices                                   |
| 0x24   | u32      | indexCount        | número de índices                                    |
| 0x28   | u8       | indexType         | `0` = u16, `1` = u32                                 |
| 0x29   | u8       | lodCount          | entradas na tabela de LODs                           |
| 0x2A   | u32      | boneCount         | reservado (0 nesta fase)                             |
| 0x2E   | u32      | vertexBlockSize   | bytes do bloco de vértices                           |
| 0x32   | u64      | clusterBlockOffset| offset absoluto do bloco de clusters (0 se ausente)  |
| 0x3A   | u64      | clusterBlockSize  | tamanho do bloco de clusters (0 se ausente)          |
| 0x42..0x4F | —    | reservado         | zeros (completa os 80 bytes do header)               |

## Bloco de vértices (24 bytes por vértice)

| Bytes | Tipo     | Campo          | Descrição                                                     |
|-------|----------|----------------|---------------------------------------------------------------|
| 0..5  | u16[3]   | position       | quantizado 16 bits por eixo: `v = min + (u/65535) * (max-min)`|
| 6..7  | u16      | normal         | octaédrico 8:8 (nx no byte alto, ny no baixo; nz reconstruído)|
| 8..9  | u16      | tangent        | octaédrico 8:8 (direção); handedness fica +1 nesta fase       |
| 10..13| f16[2]   | uv             | half-float (IEEE 754 binary16)                                |
| 14..17| u8[4]    | boneIndices    | reservado p/ skinning: 4 índices de osso (0 se sem skin)      |
| 18..21| u8[4]    | boneWeights    | reservado p/ skinning: 4 pesos normalizados (soma = 255)      |
| 22..23| —        | padding        | zeros (alinhamento de 24 bytes por vértice)                   |

## Tabela de LODs (32 bytes por entrada, lodCount entradas)

| Bytes | Tipo   | Campo        | Descrição                                    |
|-------|--------|--------------|----------------------------------------------|
| 0..3  | u32    | indexOffset  | início dos índices do LOD (em índices)       |
| 4..7  | u32    | indexCount   | quantidade de índices do LOD                 |
| 8..31 | f32[6] | bounds       | bounding box do LOD (min xyz, max xyz)       |

> Correção (2026-11-21): esta tabela dizia "20 bytes" desde a Fase 1; o código
> real usa 32 (fix F2.2 — o buffer do escritor estourava com 20).

## Bloco de índices

`indexCount` valores u16 ou u32 (conforme `indexType`).

## Bloco de ossos (reservado — boneCount entradas, 0 nesta fase)

| Bytes        | Tipo    | Campo             | Descrição                                    |
|--------------|---------|-------------------|----------------------------------------------|
| 0..3         | i32     | parentIndex       | índice do pai (-1 = raiz)                    |
| 4..5         | u16     | nameLength        | comprimento do nome                          |
| 6..6+n       | char[n] | name              | nome UTF-8 do osso                           |
| ...          | f32[16] | inverseBindMatrix | bind pose inversa (column-major)             |

## Bloco de clusters (opcional — reserva para a KENA)

Bytes opacos; o subformato (meshlets, cones de culling, LOD contínuo) será
definido na Fase da KENA. O header aponta offset e tamanho.

## Grupos e materiais (versão 2, se `flags & 2 != 0`)

Após os ossos e os bytes opcionais de clusters vêm `u16 groupCount`, seguidos de `groupCount` triplas `(u32 indexOffset, u32 indexCount, u32 materialIndex)`. O offset conta índices, não bytes. Depois vêm `u16 materialCount` e os registros dos materiais, na ordem:

1. Nome (comprimento `u8` em bytes + bytes UTF-8).
2. `u8[4]` de cor base RGBA, `f32 metallic`, `f32 roughness`.
3. `u8` de flags: bit 0 = textura base, 1 = normal map, 2 = dupla face.
4. Caminho da textura base e caminho da normal, cada um com comprimento `u8` em bytes + bytes UTF-8 (comprimento zero é válido).

Uma malha sem grupos nem materiais pode usar v2 sem o bit 1 e não tem essa tabela. Armazenar os fatores do material não significa que o renderer já implemente todo o pipeline PBR. O layout nesta documentação descreve `assets/mesh/KernWriter.kt`; regressões de leitura e escrita ficam em `assets/mesh` na suíte JVM.
