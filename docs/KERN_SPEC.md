# KERN — Formato de malha 3D da KENKERN (versão 1)

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
| 0x04   | u16      | version           | `1` nesta fase                                       |
| 0x06   | u16      | flags             | bit 0 = possui bloco de clusters                     |
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
