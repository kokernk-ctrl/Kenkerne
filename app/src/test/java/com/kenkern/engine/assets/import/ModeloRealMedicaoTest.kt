package com.kenkern.engine.assets.import

import com.kenkern.engine.assets.image.KimgPipeline
import com.kenkern.engine.assets.image.KimgReader
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * MEDIÇÃO em MODELOS REAIS (auditoria v0.1.15 — V2): roda a MESMA pipeline do
 * botão Importar (ModelImporter → .KERN + texturas → KimgPipeline/.KIMG) sobre
 * modelos reais e livres do Khronos glTF-Sample-Assets e imprime a tabela de
 * compressão (tamanhos, razão, vértices antes/depois, PSNR, tempo, memória).
 *
 * MODELOS (não vão para o git — baixados de KhronosGroup/glTF-Sample-Assets):
 *   DamagedHelmet (GLB), FlightHelmet (glTF+bin+PNG), Sponza (glTF+bin+JPEG),
 *   BrainStem (GLB, com esqueleto — exercita o aviso "skinning ignorado").
 * Requer -Dkenkern.modelos.dir=<pasta>. Sem a pasta, o teste é IGNORADO
 * (Assume) — o CI segue verde; local com a pasta roda a medição completa.
 *
 * NOTA HONESTA: no aparelho o decode de PNG/JPEG é android.graphics
 * (KimgConversor); aqui é javax.imageio da JVM via REFLEXÃO (android.jar de
 * compilação não expõe javax.imageio; na execução do teste a JVM real fornece).
 * ETC2/PSNR/tamanhos são os MESMOS (KimgPipeline é Kotlin puro) — só o decoder
 * de origem difere.
 */
class ModeloRealMedicaoTest {

    private val dirModelos: File?
        get() = System.getProperty("kenkern.modelos.dir")?.let { File(it) }?.takeIf { it.isDirectory }

    @Test
    fun medir_modelos_reais() {
        assumeTrue(
            "pasta de modelos reais ausente (-Dkenkern.modelos.dir) — teste IGNORADO no CI",
            dirModelos != null
        )
        val dir = dirModelos!!

        val saida = StringBuilder()
        saida.appendLine("=== MEDIÇÃO V2 — modelos reais (Khronos glTF-Sample-Assets) ===")
        saida.appendLine()

        for (modelo in listOf("DamagedHelmet", "FlightHelmet", "Sponza", "BrainStem")) {
            val pasta = File(dir, modelo)
            assertTrue("Modelo obrigatório ausente: $modelo", pasta.isDirectory)
            saida.appendLine("--- $modelo ---")
            saida.appendLine(medirModelo(modelo, pasta))
            saida.appendLine()
        }
        System.out.println(saida)
        File("build/medicao-real.txt").writeText(saida.toString())
    }

    // ------------------------------------------------------------------ núcleo

    private fun medirModelo(nome: String, pasta: File): String {
        val arquivos = pasta.listFiles()!!.filter { it.isFile }
        val glb = arquivos.firstOrNull { it.extension.equals("glb", true) }
        val gltf = arquivos.firstOrNull { it.extension.equals("gltf", true) }
        val bytesOrigem: ByteArray
        var resolutor: ResolvedorExterno? = null
        var bytesPasta = 0L

        if (glb != null) {
            bytesOrigem = glb.readBytes()
            bytesPasta = bytesOrigem.size.toLong()
        } else if (gltf != null) {
            bytesOrigem = gltf.readBytes()
            val mapa = HashMap<String, ByteArray>()
            for (f in arquivos) mapa[f.name] = f.readBytes()
            for (f in arquivos) bytesPasta += f.length()
            resolutor = object : ResolvedorExterno {
                override fun bytes(caminhoRelativo: String): ByteArray? {
                    // URIs são relativas ao .gltf; tenta caminho relativo, pasta e só o nome.
                    mapa[caminhoRelativo]?.let { return it }
                    return mapa[caminhoRelativo.substringAfterLast('/')]
                }
            }
        } else {
            error("sem .glb nem .gltf em $pasta")
        }

        val t0 = System.nanoTime()
        val summary = ModelImporter.importarModelo(
            (glb ?: gltf!!).name, bytesOrigem,
            resolutorExterno = resolutor
        )
        val tempoImportMs = (System.nanoTime() - t0) / 1_000_000L

        assertTrue("$nome: ${summary.problemasIntegridade}", summary.integridadeOk)
        assertTrue("$nome sem geometria", summary.vertices > 0 && summary.triangulos > 0 && summary.bytesKern > 0)

        // ---- texturas: pipeline COMPLETA como o botão faz (decode → ETC2 → .KIMG)
        var kimgCru = 0L          // .KIMG sem camada Deflate (formato v1)
        var kimgDeflate = 0L      // .KIMG com camada Deflate (v2 quando ganha)
        var gpuEtc2 = 0L          // memória de GPU real (soma dos mips ETC2)
        var gpuRgba8 = 0L         // memória de GPU se fosse RGBA8_RAW (com cadeia de mips)
        var psnrPeso = 0.0
        var psnrPesoTex = 0.0
        var tempoEtc2Ms = 0L
        var tempoDeflateMs = 0L
        var tempoLeituraMs = 0L
        val detalhes = ArrayList<String>(summary.texturasExtraidas.size)

        for ((idx, tex) in summary.texturasExtraidas.withIndex()) {
            val dec = decodificar(tex.bytes) ?: error("$nome: textura ilegível ${tex.nomeOrigem}")
            val t1 = System.nanoTime()
            val (cru, relCru) = KimgPipeline.converter(
                dec.rgba, dec.w, dec.h, limiteTextura = 0
            )
            val t2 = System.nanoTime()
            // Variante com camada em disco: REAPROVEITA os blocos ETC2 já
            // codificados (lê o .KIMG cru e regrava com o mesmo conteúdo —
            // resultado idêntico ao converter(comprimirEmDisco=true), sem o
            // custo de re-encodar ETC2).
            val zip = com.kenkern.engine.assets.image.KimgWriter.write(
                com.kenkern.engine.assets.image.KimgReader.read(cru),
                comprimirEmDisco = true
            )
            val t3 = System.nanoTime()
            tempoEtc2Ms += (t2 - t1) / 1_000_000L
            tempoDeflateMs += (t3 - t2) / 1_000_000L

            // carga real (o que o aparelho fará ao REABRIR: inflar + montar mips)
            val t4 = System.nanoTime()
            val imagem = KimgReader.read(zip)
            tempoLeituraMs += (System.nanoTime() - t4) / 1_000_000L
            val gpu = imagem.mips.sumOf { it.dados.size.toLong() }
            gpuEtc2 += gpu
            // RGBA8 equivalente: mesma cadeia de mips a 4 bytes/px
            var w = imagem.largura
            var h = imagem.altura
            var rgbaChain = 0L
            do {
                rgbaChain += (w.toLong() * h) * 4
                w = (w / 2).coerceAtLeast(1); h = (h / 2).coerceAtLeast(1)
            } while (!(w == 1 && h == 1))
            rgbaChain += 4L // mip 1×1
            gpuRgba8 += rgbaChain

            kimgCru += cru.size
            kimgDeflate += zip.size
            val peso = relCru.bytesKimg.toDouble()
            psnrPeso += relCru.psnrDb * peso
            psnrPesoTex += peso
            detalhes.add(
                "  tex %02d %-44s %dx%d %s origem=%d cru=%d deflate=%d PSNR=%.1f dB"
                    .format(
                        idx + 1, tex.nomeOrigem.take(44), dec.w, dec.h, imagem.formato.rotulo,
                        tex.bytes.size, cru.size, zip.size, relCru.psnrDb
                    )
            )
        }

        val psnrMedio = if (psnrPesoTex > 0) psnrPeso / psnrPesoTex else 0.0
        val origemTotal = bytesPasta
        val finalSemCamada = summary.bytesKern + kimgCru
        val finalComCamada = summary.bytesKern + kimgDeflate
        val razaoSem = if (finalSemCamada > 0) origemTotal.toDouble() / finalSemCamada else 0.0
        val razaoCom = if (finalComCamada > 0) origemTotal.toDouble() / finalComCamada else 0.0
        val ganhoDeflate = if (kimgCru > 0) (1.0 - kimgDeflate.toDouble() / kimgCru) * 100 else 0.0

        return buildString {
            appendLine("arquivos de origem: %d | total em disco: %.1f MB".format(arquivos.size, origemTotal / 1048576.0))
            appendLine("formato: ${summary.formato} | esqueleto: ${if (summary.contemEsqueleto) "SIM (ignorado nesta fase)" else "não"}")
            appendLine("vértices ANTES (fonte): ${verticesFonte(bytesOrigem)} | depois (dedup/LOD): ${summary.vertices}")
            appendLine("triângulos ANTES (fonte): ${triangulosFonte(bytesOrigem)} | depois (triangulado): ${summary.triangulos}")
            appendLine("LODs: ${summary.lods} | materiais: ${summary.materiais} | texturas: ${summary.texturas} | erro máx quantização: ${"%.2f".format(summary.erroMaxQuantizacao)} passo(s)")
            appendLine(".KERN: %.1f KB".format(summary.bytesKern / 1024.0))
            appendLine(".KIMG total SEM camada Deflate: %.1f KB (%d texturas)".format(kimgCru / 1024.0, summary.texturasExtraidas.size))
            appendLine(".KIMG total COM camada Deflate: %.1f KB (ganho %.1f%%)".format(kimgDeflate / 1024.0, ganhoDeflate))
            appendLine("TOTAL APÓS IMPORTAÇÃO: %.1f MB sem camada | %.1f MB com camada".format(finalSemCamada / 1048576.0, finalComCamada / 1048576.0))
            appendLine("RAZÃO origem→assets: %.2f× sem camada | %.2f× com camada".format(razaoSem, razaoCom))
            appendLine("MEMÓRIA DE GPU das texturas: ETC2 %.1f MB vs RGBA8 %.1f MB (economia %.0f%%)".format(
                gpuEtc2 / 1048576.0, gpuRgba8 / 1048576.0, if (gpuRgba8 > 0) (1.0 - gpuEtc2.toDouble() / gpuRgba8) * 100 else 0.0))
            appendLine("PSNR médio das texturas: %.1f dB | tempo import: %d ms | pico mem import: %.0f MB".format(
                psnrMedio, tempoImportMs, summary.picoMemoriaMb))
            appendLine("tempo ETC2: %d ms | tempo camada Deflate: %d ms | tempo LEITURA (inflate na carga): %d ms".format(tempoEtc2Ms, tempoDeflateMs, tempoLeituraMs))
            appendLine("integridade: ${if (summary.integridadeOk) "OK" else "FALHOU: " + summary.problemasIntegridade.joinToString("; ")}")
            for (a in summary.avisos) appendLine("aviso: $a")
            if (detalhes.size <= 20) for (d in detalhes) appendLine(d)
            else { for (d in detalhes.take(10)) appendLine(d); appendLine("  … (+${detalhes.size - 10} texturas)") }
        }
    }

    private class Decodificada(val rgba: ByteArray, val w: Int, val h: Int)

    /** PNG/JPEG → RGBA via javax.imageio (REFLEXÃO — android.jar não expõe, a JVM real sim). */
    private fun decodificar(bytes: ByteArray): Decodificada? {
        try {
            val imageio = Class.forName("javax.imageio.ImageIO")
            val img = imageio.getMethod("read", java.io.InputStream::class.java)
                .invoke(null, bytes.inputStream()) ?: return null
            val cls = img.javaClass
            val w = cls.getMethod("getWidth").invoke(img) as Int
            val h = cls.getMethod("getHeight").invoke(img) as Int
            if (w <= 0 || h <= 0) return null
            // getRGB(0,0,w,h,rgbArray,offset,scanline) — UMA chamada por textura
            val pixels = IntArray(w * h)
            // getRGB(startX,startY,w,h,rgbArray,offset,scanline) — 7 params, UMA chamada por textura
            cls.getMethod("getRGB", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, IntArray::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(img, 0, 0, w, h, pixels, 0, w)
            val rgba = ByteArray(w * h * 4)
            var i = 0
            for (p in pixels) {
                rgba[i] = ((p shr 16) and 0xFF).toByte()
                rgba[i + 1] = ((p shr 8) and 0xFF).toByte()
                rgba[i + 2] = (p and 0xFF).toByte()
                rgba[i + 3] = ((p shr 24) and 0xFF).toByte()
                i += 4
            }
            return Decodificada(rgba, w, h)
        } catch (e: ClassNotFoundException) {
            System.out.println("decode: java.desktop ausente — " + e)
            return null // sem java.desktop: testes de medição rodariam só com PNG puro
        } catch (e: Exception) {
            System.out.println("decode falhou (" + e.javaClass.simpleName + "): " + e.message) // diagnóstico
            return null // formato não decodificável (o relatório lista)
        }
    }

    /** Contadores "ANTES" lidos do glTF/GLB de origem (acessors, antes do dedup). */
    private fun verticesFonte(bytes: ByteArray): Int = contarFonte(bytes) { v, _ -> v }

    private fun triangulosFonte(bytes: ByteArray): Int = contarFonte(bytes) { _, ind -> ind / 3 }

    private fun contarFonte(bytes: ByteArray, acao: (Int, Int) -> Int): Int {
        try {
            val obj = org.json.JSONObject(extrairJsonGltf(bytes))
            val acessors = obj.getJSONArray("accessors")
            var verts = 0
            var indices = 0
            val meshes = obj.getJSONArray("meshes")
            for (m in 0 until meshes.length()) {
                val prims = meshes.getJSONObject(m).getJSONArray("primitives")
                for (p in 0 until prims.length()) {
                    val prim = prims.getJSONObject(p)
                    val attrs = prim.getJSONObject("attributes")
                    if (attrs.has("POSITION")) {
                        verts += acessors.getJSONObject(attrs.getInt("POSITION")).getInt("count")
                    }
                    if (prim.has("indices")) {
                        indices += acessors.getJSONObject(prim.getInt("indices")).getInt("count")
                    }
                }
            }
            return acao(verts, indices)
        } catch (e: Exception) {
            return -1
        }
    }

    private fun extrairJsonGltf(bytes: ByteArray): String {
        // GLB: JSON no primeiro chunk (offset 20); glTF JSON puro: é o próprio arquivo.
        if (bytes.size >= 20 && bytes[0] == 'g'.code.toByte() && bytes[1] == 'l'.code.toByte()) {
            val b12 = bytes[12].toInt() and 0xFF
            val b13 = bytes[13].toInt() and 0xFF
            val b14 = bytes[14].toInt() and 0xFF
            val b15 = bytes[15].toInt() and 0xFF
            val len = b12 or (b13 shl 8) or (b14 shl 16) or (b15 shl 24)
            return String(bytes, 20, len, Charsets.UTF_8)
        }
        return String(bytes, Charsets.UTF_8)
    }
}
