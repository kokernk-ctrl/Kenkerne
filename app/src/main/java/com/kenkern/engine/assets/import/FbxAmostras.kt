package com.kenkern.engine.assets.import

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

/**
 * AMOSTRAS FBX SINTÉTICAS para o teste do importador (JVM) e para a checagem
 * do Diagnóstico (F2.8). NÃO são fixtures mortas: cada amostra exercita um
 * caminho diferente do importador (polígonos/ngon, mappings de normal e UV,
 * Direct/IndexToDirect, arrays zlib, versão 7500 com headers u64, FBX ASCII,
 * FBX 6.x).
 *
 * O mini-escritor abaixo gera FBX binário VÁLIDO (mesma estrutura de records
 * que o Blender/Assimp produzem) — só dependem de java.*, rodam na JVM dos
 * testes e no aparelho.
 */
object FbxAmostras {

    // ---------------------------------------------------------------- amostras

    /**
     * QUAD (2 triângulos) com Vertices, PolygonVertexIndex, NORMAL
     * (ByPolygonVertex/Direct) e UV (ByPolygonVertex/IndexToDirect) — v7400.
     */
    fun quadFbx(): ByteArray {
        val vertices = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0)
        val pvi = intArrayOf(0, 1, -3, 0, 2, -4) // 2 triângulos (fim de polígono = negativo)
        val normais = DoubleArray(18) { i -> if (i % 3 == 2) 1.0 else 0.0 } // 6 × (0,0,1)
        val uvs = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        val uvIndex = intArrayOf(0, 1, 2, 0, 2, 3)
        return arquivo(7400, quadNo(vertices, pvi, normais, uvs, uvIndex))
    }

    /**
     * UM único polígono de 4 cantos (quad/ngon) — triangulação em leque
     * deve produzir 6 índices e 4 vértices expandidos.
     */
    fun ngonQuadFbx(): ByteArray {
        val vertices = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0)
        val pvi = intArrayOf(0, 1, 2, -4) // 1 polígono de 4 cantos
        val normais = DoubleArray(12) { i -> if (i % 3 == 2) 1.0 else 0.0 } // 4 × (0,0,1)
        return arquivo(7400, quadNo(vertices, pvi, normais, null, null))
    }

    /** Normais por VÉRTICE (ByVertice/Direct) — 4 normais para 4 control points. */
    fun normaisPorVerticeFbx(): ByteArray {
        val vertices = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0)
        val pvi = intArrayOf(0, 1, -3, 0, 2, -4)
        val normais = DoubleArray(12) { i -> if (i % 3 == 2) 1.0 else 0.0 } // 4 × (0,0,1) por cp
        val geometry = NoFBX(
            "Geometry",
            listOf(1000L, "Geometry::quad", "Mesh"),
            listOf(
                NoFBX("GeometryVersion", listOf(124L)),
                NoFBX("Vertices", listOf(Arr('d', 0, bytesDoubles(vertices)))),
                NoFBX("PolygonVertexIndex", listOf(Arr('i', 0, bytesInts(pvi)))),
                NoFBX("LayerElementNormal", listOf(0L, "", "ElementNormal"), listOf(
                    NoFBX("Version", listOf(101L)),
                    NoFBX("Name", listOf("")),
                    NoFBX("MappingInformationType", listOf("ByVertice")),
                    NoFBX("ReferenceInformationType", listOf("Direct")),
                    NoFBX("Normals", listOf(Arr('d', 0, bytesDoubles(normais))))
                ))
            )
        )
        return arquivo(7400, geometry)
    }

    /** SEM LayerElementNormal e SEM UV — importador DEVE acumular normais por face. */
    fun semNormaisFbx(): ByteArray {
        val vertices = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0)
        val pvi = intArrayOf(0, 1, -3, 0, 2, -4)
        val geometry = NoFBX(
            "Geometry",
            listOf(1000L, "Geometry::quad", "Mesh"),
            listOf(
                NoFBX("GeometryVersion", listOf(124L)),
                NoFBX("Vertices", listOf(Arr('d', 0, bytesDoubles(vertices)))),
                NoFBX("PolygonVertexIndex", listOf(Arr('i', 0, bytesInts(pvi))))
            )
        )
        return arquivo(7400, geometry)
    }

    /** Vertices e Normals com encoding 1 (zlib) — caminho do Inflater. */
    fun zlibFbx(): ByteArray {
        val vertices = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0)
        val pvi = intArrayOf(0, 1, -3, 0, 2, -4)
        val normais = DoubleArray(18) { i -> if (i % 3 == 2) 1.0 else 0.0 }
        val geometry = NoFBX(
            "Geometry",
            listOf(1000L, "Geometry::quad", "Mesh"),
            listOf(
                NoFBX("GeometryVersion", listOf(124L)),
                NoFBX("Vertices", listOf(Arr('d', 1, bytesDoubles(vertices)))),
                NoFBX("PolygonVertexIndex", listOf(Arr('i', 0, bytesInts(pvi)))),
                NoFBX("LayerElementNormal", listOf(0L, "", "ElementNormal"), listOf(
                    NoFBX("Version", listOf(101L)),
                    NoFBX("Name", listOf("")),
                    NoFBX("MappingInformationType", listOf("ByPolygonVertex")),
                    NoFBX("ReferenceInformationType", listOf("Direct")),
                    NoFBX("Normals", listOf(Arr('d', 1, bytesDoubles(normais))))
                ))
            )
        )
        return arquivo(7400, geometry)
    }

    /**
     * CUBO em 6 polígonos de quad, 1 GEOMETRY compartilhada por 2 MODELS:
     * A com Lcl Rotation (0,90,0) + Lcl Scaling (2,1,1); B com Lcl Translation
     * (0,3,0) — valida hierarquia, TRS em graus e MERGE de instâncias
     * (24 vértices × 2 = 48, 36 índices × 2 = 72).
     */
    fun cuboCompartilhadoFbx(): ByteArray {
        val h = 0.5
        val vertices = doubleArrayOf(
            -h, -h, h,  h, -h, h,  h, h, h,  -h, h, h,    // frente z+
            -h, -h, -h,  h, -h, -h,  h, h, -h,  -h, h, -h // trás z-
        )
        // 6 polígonos de quad (último canto = índice negativo)
        val pvi = intArrayOf(
            0, 1, 2, -4,    // frente
            5, 4, 7, -7,    // trás
            4, 0, 3, -8,    // esquerda
            1, 5, 6, -3,    // direita
            3, 2, 6, -8,    // topo
            4, 5, 1, -1     // base
        )
        val geometry = NoFBX(
            "Geometry",
            listOf(1000L, "Geometry::cubo", "Mesh"),
            listOf(
                NoFBX("GeometryVersion", listOf(124L)),
                NoFBX("Vertices", listOf(Arr('d', 0, bytesDoubles(vertices)))),
                NoFBX("PolygonVertexIndex", listOf(Arr('i', 0, bytesInts(pvi))))
            )
        )
        val modelA = NoFBX(
            "Model",
            listOf(2000L, "Model::A", "Mesh"),
            listOf(
                NoFBX("Version", listOf(232L)),
                NoFBX("Properties70", emptyList(), listOf(
                    p("Lcl Rotation", 0.0, 90.0, 0.0),
                    p("Lcl Scaling", 2.0, 1.0, 1.0)
                ))
            )
        )
        val modelB = NoFBX(
            "Model",
            listOf(2001L, "Model::B", "Mesh"),
            listOf(
                NoFBX("Version", listOf(232L)),
                NoFBX("Properties70", emptyList(), listOf(
                    p("Lcl Translation", 0.0, 3.0, 0.0)
                ))
            )
        )
        val objects = NoFBX("Objects", emptyList(), listOf(geometry, modelA, modelB))
        val connections = NoFBX("Connections", emptyList(), listOf(
            NoFBX("C", listOf("OO", 1000L, 2000L)),
            NoFBX("C", listOf("OO", 1000L, 2001L)),
            NoFBX("C", listOf("OO", 2000L, 0L)),
            NoFBX("C", listOf("OO", 2001L, 0L))
        ))
        return arquivo(7400, objects, connections)
    }

    /** Mesmo quad, versão 7500 — records com offsets u64 (25-byte headers). */
    fun v7500Fbx(): ByteArray {
        val vertices = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0)
        val pvi = intArrayOf(0, 1, -3, 0, 2, -4)
        return arquivo(7500, quadNo(vertices, pvi, null, null, null))
    }

    /** FBX ASCII (texto) mínimo SÓ com header — ainda dá erro claro (sem Objects). */
    fun asciiFbx(): ByteArray = """
FBXHeaderExtension:  {
    FBXHeaderVersion: 1003
    FBXVersion: 7400
}
""".trimIndent().toByteArray(Charsets.US_ASCII)

    /**
     * FBX ASCII COMPLETO (ETAPA B.2): o MESMO quad do [quadFbx] escrito em
     * texto (Vertices/PolygonVertexIndex/Normais/UV + Model + Connections) —
     * o parser ASCII deve produzir a MESMA saída do binário.
     */
    fun quadAsciiFbx(): ByteArray = """
FBXHeaderExtension:  {
    FBXHeaderVersion: 1003
    FBXVersion: 7400
    Creator: "KenkernTeste"
}
GlobalSettings:  {
    Version: 1000
    Properties70:  {
        P: "UnitScaleFactor", "double", "Number", "",1
    }
}
Objects:  {
    Geometry: 1000, "Geometry::quad", "Mesh" {
        GeometryVersion: 124
        Vertices: *12 {
            a: 0,0,0,1,0,0,1,1,0,0,1,0
        }
        PolygonVertexIndex: *6 {
            a: 0,1,-3,0,2,-4
        }
        LayerElementNormal: 0 {
            Version: 101
            Name: ""
            MappingInformationType: "ByPolygonVertex"
            ReferenceInformationType: "Direct"
            Normals: *18 {
                a: 0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1
            }
        }
        LayerElementUV: 0 {
            Version: 101
            Name: ""
            MappingInformationType: "ByPolygonVertex"
            ReferenceInformationType: "IndexToDirect"
            UV: *8 {
                a: 0,0,1,0,1,1,0,1
            }
            UVIndex: *6 {
                a: 0,1,2,0,2,3
            }
        }
        Layer: 0 {
            Version: 100
            LayerElement:  {
                Type: "LayerElementNormal"
                TypedIndex: 0
            }
        }
    }
    Model: 2000, "Model::quad", "Mesh" {
        Version: 232
        Properties70:  {
            P: "Lcl Translation", "Lcl Translation", "", "A",2,0,0
        }
        Shading: T
        Culling: "CullingOff"
    }
}
Connections:  {
    ;Model::quad, Model::RootNode
    C: "OO",2000,0

    ;Geometry::quad, Model::quad
    C: "OO",1000,2000
}
""".trimIndent().toByteArray(Charsets.UTF_8)

    /**
     * FBX ASCII com MATERIAL (ETAPA B.3): quad + Material com DiffuseColor
     * conectado ao Model — o importador deve criar o material e o grupo.
     */
    fun quadAsciiComMaterialFbx(): ByteArray = """
FBXHeaderExtension:  {
    FBXHeaderVersion: 1003
    FBXVersion: 7400
}
Objects:  {
    Geometry: 1000, "Geometry::quad", "Mesh" {
        Vertices: *12 {
            a: 0,0,0,1,0,0,1,1,0,0,1,0
        }
        PolygonVertexIndex: *6 {
            a: 0,1,-3,0,2,-4
        }
    }
    Model: 2000, "Model::quad", "Mesh" {
        Version: 232
    }
    Material: 3000, "Material::vermelho", "" {
        Version: 102
        ShadingModel: "phong"
        Properties70:  {
            P: "DiffuseColor", "Color", "", "A",0.9,0.1,0.1
            P: "Opacity", "double", "Number", "",0.95
        }
    }
}
Connections:  {
    C: "OO",1000,2000
    C: "OO",2000,0
    C: "OO",3000,2000
}
""".trimIndent().toByteArray(Charsets.UTF_8)

    /** Arquivo que NÃO é FBX (magic quebrado) — erro claro. */
    fun magicQuebrado(): ByteArray = "XXXXarquivo-não-FBX".toByteArray(Charsets.US_ASCII)

    /**
     * FBX ASCII com material + Video SEM Content (textura externa/procedural,
     * auditoria V3): o importador DEVE avisar no ImportReport que a textura é
     * ignorada nesta fase (cor sólida) — nunca silêncio.
     */
    fun quadAsciiComTexturaExternaFbx(): ByteArray = """
FBXHeaderExtension:  {
    FBXHeaderVersion: 1003
    FBXVersion: 7400
}
Objects:  {
    Geometry: 1000, "Geometry::quad", "Mesh" {
        Vertices: *12 {
            a: 0,0,0,1,0,0,1,1,0,0,1,0
        }
        PolygonVertexIndex: *6 {
            a: 0,1,-3,0,2,-4
        }
    }
    Model: 2000, "Model::quad", "Mesh" {
        Version: 232
    }
    Material: 3000, "Material::com-textura", "" {
        Version: 102
        ShadingModel: "phong"
        Properties70:  {
            P: "DiffuseColor", "Color", "", "A",0.2,0.4,0.8
        }
    }
    Video: 4000, "Video::textura_externa", "Clip" {
        Type: "Clip"
        Filename: "textura_externa.png"
    }
}
Connections:  {
    C: "OO",1000,2000
    C: "OO",2000,0
    C: "OO",3000,2000
    C: "OO",4000,3000
}
""".trimIndent().toByteArray(Charsets.UTF_8)

    /** FBX binário de versão 6100 (FBX 6.x) — erro claro pedindo 7.x. */
    fun versao6100Fbx(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(bytesInts(intArrayOf(6100)))
        out.write(ByteArray(13))
        return out.toByteArray()
    }

    /** PolygonVertexIndex sem índice negativo de fechamento — erro claro. */
    fun poligonoAbertoFbx(): ByteArray {
        val vertices = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0)
        val pvi = intArrayOf(0, 1, 2) // sem fechamento negativo!
        val geometry = NoFBX(
            "Geometry",
            listOf(1000L, "Geometry::quebrada", "Mesh"),
            listOf(
                NoFBX("GeometryVersion", listOf(124L)),
                NoFBX("Vertices", listOf(Arr('d', 0, bytesDoubles(vertices)))),
                NoFBX("PolygonVertexIndex", listOf(Arr('i', 0, bytesInts(pvi))))
            )
        )
        return arquivo(7400, geometry)
    }

    /** Árvore binária sintética além do limite de profundidade compartilhado com ASCII. */
    fun aninhamentoExcessivoFbx(): ByteArray {
        var node = NoFBX("Leaf", emptyList())
        for (i in 0 until 72) node = NoFBX("Deep$i", emptyList(), listOf(node))
        return arquivo(7400, node)
    }

    // ---------------------------------------------------------------- mini-escritor FBX binário

    /** Propriedade P do Properties70: nome + 3 valores 'D'. */
    private fun p(nome: String, x: Double, y: Double, z: Double) =
        NoFBX("P", listOf(nome, nome, "", "A", x, y, z))

    private class NoFBX(val nome: String, val props: List<Any>, val filhos: List<NoFBX> = emptyList())

    /** Array tipado: tipo 'd'/'i', encoding 0 raw / 1 zlib, dados little-endian. */
    private class Arr(val tipo: Char, val encoding: Int, val dados: ByteArray)

    private val MAGIC = "Kaydara FBX Binary  ".toByteArray(Charsets.US_ASCII) +
        byteArrayOf(0, 0x1A, 0)

    private fun quadNo(
        vertices: DoubleArray,
        pvi: IntArray,
        normais: DoubleArray?,
        uvs: DoubleArray?,
        uvIndex: IntArray?
    ): NoFBX {
        val filhos = ArrayList<NoFBX>(5)
        filhos.add(NoFBX("GeometryVersion", listOf(124L)))
        filhos.add(NoFBX("Vertices", listOf(Arr('d', 0, bytesDoubles(vertices)))))
        filhos.add(NoFBX("PolygonVertexIndex", listOf(Arr('i', 0, bytesInts(pvi)))))
        if (normais != null) {
            filhos.add(NoFBX("LayerElementNormal", listOf(0L, "", "ElementNormal"), listOf(
                NoFBX("Version", listOf(101L)),
                NoFBX("Name", listOf("")),
                NoFBX("MappingInformationType", listOf("ByPolygonVertex")),
                NoFBX("ReferenceInformationType", listOf("Direct")),
                NoFBX("Normals", listOf(Arr('d', 0, bytesDoubles(normais))))
            )))
        }
        if (uvs != null && uvIndex != null) {
            filhos.add(NoFBX("LayerElementUV", listOf(0L, "", "ElementUV"), listOf(
                NoFBX("Version", listOf(101L)),
                NoFBX("Name", listOf("")),
                NoFBX("MappingInformationType", listOf("ByPolygonVertex")),
                NoFBX("ReferenceInformationType", listOf("IndexToDirect")),
                NoFBX("UV", listOf(Arr('d', 0, bytesDoubles(uvs)))),
                NoFBX("UVIndex", listOf(Arr('i', 0, bytesInts(uvIndex))))
            )))
        }
        val geometry = NoFBX(
            "Geometry", listOf(1000L, "Geometry::quad", "Mesh"), filhos
        )
        return geometry
    }

    /** Monta o arquivo: Geometry + Model default + Connections + terminadores. */
    private fun arquivo(versao: Int, geometry: NoFBX): ByteArray {
        val objects = NoFBX("Objects", emptyList(), listOf(
            geometry,
            NoFBX("Model", listOf(2000L, "Model::quad", "Mesh"), listOf(
                NoFBX("Version", listOf(232L))
            ))
        ))
        val connections = NoFBX("Connections", emptyList(), listOf(
            NoFBX("C", listOf("OO", 1000L, 2000L)),
            NoFBX("C", listOf("OO", 2000L, 0L))
        ))
        return arquivo(versao, objects, connections)
    }

    private fun arquivo(versao: Int, objects: NoFBX, connections: NoFBX): ByteArray {
        val global = NoFBX("GlobalSettings", emptyList(), listOf(
            NoFBX("Version", listOf(100L)),
            NoFBX("Properties70", emptyList(), listOf(
                NoFBX("P", listOf("UnitScaleFactor", "double", "Number", "", 1.0))
            ))
        ))
        val raizes = listOf(
            NoFBX("FBXHeaderExtension", emptyList(), listOf(
                NoFBX("FBXHeaderVersion", listOf(1003L)),
                NoFBX("FBXVersion", listOf(versao.toLong()))
            )),
            global,
            objects,
            connections
        )
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(bytesInts(intArrayOf(versao)))
        for (r in raizes) escreverNo(out, r, versao)
        out.write(ByteArray(if (versao >= 7500) 25 else 13)) // terminador de nível de topo
        return out.toByteArray()
    }

    private fun escreverNo(out: ByteArrayOutputStream, no: NoFBX, versao: Int) {
        // endOffset do FBX é ABSOLUTO no arquivo: antes de escrever, calcula o
        // tamanho total da subárvore e soma à posição atual do arquivo.
        val headerSize = if (versao >= 7500) 25 else 13
        val nomeBytes = no.nome.toByteArray(Charsets.US_ASCII)
        val endOffset = out.size() + tamanhoNo(no, versao)
        val propsBuf = ByteArrayOutputStream()
        for (prop in no.props) escreverProp(propsBuf, prop, versao)
        val cab = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN)
        if (versao >= 7500) {
            cab.putLong(endOffset.toLong())
            cab.putLong(no.props.size.toLong())
            cab.putLong(propsBuf.size().toLong())
        } else {
            cab.putInt(endOffset)
            cab.putInt(no.props.size)
            cab.putInt(propsBuf.size())
        }
        cab.put(nomeBytes.size.toByte())
        out.write(cab.array())
        out.write(nomeBytes)
        propsBuf.writeTo(out)
        if (no.filhos.isNotEmpty()) {
            for (f in no.filhos) escreverNo(out, f, versao) // direto no arquivo (offsets absolutos)
            out.write(ByteArray(if (versao >= 7500) 25 else 13)) // terminador dos filhos
        }
    }

    /** Tamanho total da subárvore do node (header + nome + props + filhos + terminador). */
    private fun tamanhoNo(no: NoFBX, versao: Int): Int {
        val headerSize = if (versao >= 7500) 25 else 13
        val propsBuf = ByteArrayOutputStream()
        for (prop in no.props) escreverProp(propsBuf, prop, versao)
        var total = headerSize + no.nome.length + propsBuf.size()
        if (no.filhos.isNotEmpty()) {
            for (f in no.filhos) total += tamanhoNo(f, versao)
            total += if (versao >= 7500) 25 else 13
        }
        return total
    }

    private fun escreverProp(out: ByteArrayOutputStream, prop: Any, versao: Int) {
        when (prop) {
            is Long -> {
                out.write('L'.code)
                out.write(bytesLongs(longArrayOf(prop)))
            }
            is Int -> {
                out.write('I'.code)
                out.write(bytesInts(intArrayOf(prop)))
            }
            is Double -> {
                out.write('D'.code)
                out.write(bytesDoubles(doubleArrayOf(prop)))
            }
            is String -> {
                out.write('S'.code)
                val b = prop.toByteArray(Charsets.UTF_8)
                out.write(bytesInts(intArrayOf(b.size)))
                out.write(b)
            }
            is Arr -> {
                out.write(prop.tipo.code)
                val elementos = when (prop.tipo) {
                    'd' -> prop.dados.size / 8
                    'i' -> prop.dados.size / 4
                    else -> prop.dados.size
                }
                val payload = if (prop.encoding == 1) deflate(prop.dados) else prop.dados
                out.write(bytesInts(intArrayOf(elementos)))
                out.write(bytesInts(intArrayOf(prop.encoding)))
                out.write(bytesInts(intArrayOf(payload.size)))
                out.write(payload)
            }
            else -> throw IllegalArgumentException("propriedade de amostra não suportada: ${prop::class.java}")
        }
    }

    // ---------------------------------------------------------------- helpers de bytes

    private fun bytesDoubles(v: DoubleArray): ByteArray {
        val buf = ByteBuffer.allocate(v.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        for (x in v) buf.putDouble(x)
        return buf.array()
    }

    private fun bytesInts(v: IntArray): ByteArray {
        val buf = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (x in v) buf.putInt(x)
        return buf.array()
    }

    private fun bytesLongs(v: LongArray): ByteArray {
        val buf = ByteBuffer.allocate(v.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        for (x in v) buf.putLong(x)
        return buf.array()
    }

    private fun deflate(dados: ByteArray): ByteArray {
        val deflater = Deflater()
        return try {
            deflater.setInput(dados)
            deflater.finish()
            val saida = ByteArrayOutputStream()
            val bloco = ByteArray(4096)
            while (!deflater.finished()) {
                val n = deflater.deflate(bloco)
                saida.write(bloco, 0, n)
            }
            saida.toByteArray()
        } finally {
            deflater.end()
        }
    }
}
