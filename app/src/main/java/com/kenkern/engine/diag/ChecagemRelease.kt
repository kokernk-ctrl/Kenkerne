package com.kenkern.engine.diag

import com.kenkern.engine.assets.image.Etc2
import com.kenkern.engine.assets.image.KimgPipeline
import com.kenkern.engine.assets.image.KimgReader
import com.kenkern.engine.assets.import.FbxAmostras
import com.kenkern.engine.assets.import.FbxImporter
import com.kenkern.engine.assets.import.GltfAmostras
import com.kenkern.engine.assets.import.GltfImporter
import com.kenkern.engine.core.script.ScriptRuntime

/**
 * FUMAÇA DO RELEASE MINIFICADO (auditoria v0.1.15 — V1.5): prova NO APARELHO
 * que o R8 não removeu classe crítica do APK release. O dex da v0.1.14 foi
 * auditado com dexdump/mapping.txt (141 classes org.luaj.vm2 presentes como
 * a2.*, 22 subpacotes com.kenkern.engine intactos) — esta checagem é o guarda
 * CONTÍNUO: se uma regra R8 futura encolher algo que o release usa, o
 * Diagnóstico mostra FALHOU com o nome da classe ausente ou a operação
 * que quebrou.
 *
 * Puro Kotlin (sem android.*): a MESMA lógica roda nos testes JVM do CI e no
 * aparelho — os testes não substituem a checagem no release, complementam.
 *
 * Não usa runCatching silencioso: todo passo que falha devolve o MOTIVO no
 * resultado, e o Diagnóstico registra no KenkernLog.
 */
object ChecagemRelease {

    /** Resultado de um passo: ok/erro com detalhe (o motivo, nunca "?"). */
    class Resultado(val passo: String, val ok: Boolean, val detalhe: String)

    /**
     * Classes que o release NÃO PODE perder (nomes canônicos — se o R8 as
     * removesse, Class.forName lançaria ClassNotFoundException no aparelho).
     */
    val classesCriticas: List<String> = listOf(
        "com.kenkern.engine.assets.import.GltfImporter",
        "com.kenkern.engine.assets.import.FbxImporter",
        "com.kenkern.engine.assets.import.ModelImporter",
        "com.kenkern.engine.assets.image.KimgWriter",
        "com.kenkern.engine.assets.image.KimgReader",
        "com.kenkern.engine.assets.image.Etc2",
        "com.kenkern.engine.core.script.ScriptRuntime",
        "com.kenkern.engine.core.script.LuaJScriptRuntime",
        "com.kenkern.engine.diag.KenkernLog"
    )

    /** Executa TODOS os passos, na ordem; cada passo independente dos demais. */
    fun executar(): List<Resultado> {
        val saida = ArrayList<Resultado>(6)
        saida.add(checarClassesPorNome())
        saida.add(checarGltf())
        saida.add(checarFbx())
        saida.add(checarKimg())
        saida.add(checarEtc2())
        saida.add(checarLua())
        return saida
    }

    /** Conveniente para o Diagnóstico: resumo "ok/total" e o primeiro erro. */
    fun resumo(resultados: List<Resultado>): Pair<Int, String> {
        val ok = resultados.count { it.ok }
        val primeiroErro = resultados.firstOrNull { !it.ok }
        return ok to (primeiroErro?.detalhe ?: "—")
    }

    // ------------------------------------------------------------- passos

    private fun checarClassesPorNome(): Resultado {
        val ausentes = ArrayList<String>(0)
        for (nome in classesCriticas) {
            try {
                Class.forName(nome)
            } catch (e: ClassNotFoundException) {
                ausentes.add(nome.substringAfterLast('.'))
            }
        }
        return if (ausentes.isEmpty()) {
            Resultado(
                "classes",
                ok = true,
                "${classesCriticas.size} classes críticas presentes no dex (Class.forName)"
            )
        } else {
            Resultado("classes", ok = false, "AUSENTES no dex do release: ${ausentes.joinToString()}")
        }
    }

    private fun checarGltf(): Resultado {
        return try {
            val r = GltfImporter.importar(GltfAmostras.quadGlb())
            if (r.mesh.vertexCount == 4 && r.mesh.indices.size == 6) {
                Resultado("gltf", ok = true, "quad GLB importado: 4 vértices / 6 índices")
            } else {
                Resultado(
                    "gltf", ok = false,
                    "quad GLB: ${r.mesh.vertexCount} vértices / ${r.mesh.indices.size} índices (esperado 4/6)"
                )
            }
        } catch (e: Exception) {
            Resultado("gltf", ok = false, "exceção no importador glTF: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun checarFbx(): Resultado {
        return try {
            val r = FbxImporter.importar(FbxAmostras.quadFbx())
            if (r.mesh.vertexCount == 6 && r.mesh.indices.size == 6) {
                Resultado("fbx", ok = true, "quad FBX importado: 6 vértices / 6 índices")
            } else {
                Resultado(
                    "fbx", ok = false,
                    "quad FBX: ${r.mesh.vertexCount} vértices / ${r.mesh.indices.size} índices (esperado 6/6)"
                )
            }
        } catch (e: Exception) {
            Resultado("fbx", ok = false, "exceção no importador FBX: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun checarKimg(): Resultado {
        return try {
            val rgba = ByteArray(4 * 4 * 4) { i -> ((i * 37) % 256).toByte() }
            val (bytes, _) = KimgPipeline.converter(rgba, 4, 4, limiteTextura = 0)
            val imagem = KimgReader.read(bytes)
            if (imagem.largura == 4 && imagem.altura == 4 && imagem.mips.isNotEmpty()) {
                Resultado(
                    "kimg", ok = true,
                    "ida e volta 4×4 OK (${imagem.formato.rotulo}, ${imagem.mips.size} mip(s), ${bytes.size} bytes)"
                )
            } else {
                Resultado(
                    "kimg", ok = false,
                    "ida e volta 4×4: ${imagem.largura}×${imagem.altura}, ${imagem.mips.size} mips (esperado 4×4, ≥1)"
                )
            }
        } catch (e: Exception) {
            Resultado("kimg", ok = false, "exceção no pipeline .KIMG: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun checarEtc2(): Resultado {
        return try {
            val rgba = ByteArray(4 * 4 * 4) { i -> ((i * 53) % 256).toByte() }
            val (bytes, _) = KimgPipeline.converter(rgba, 4, 4, limiteTextura = 0)
            val imagem = KimgReader.read(bytes)
            val dec = Etc2.decodificarRgb(imagem.mips[0].dados, imagem.largura, imagem.altura)
            if (dec.size == 4 * 4 * 4) {
                Resultado("etc2", ok = true, "bloco 4×4 codificado e decodificado (${dec.size} bytes RGBA)")
            } else {
                Resultado("etc2", ok = false, "decodificação devolveu ${dec.size} bytes (esperado ${4 * 4 * 4})")
            }
        } catch (e: Exception) {
            Resultado("etc2", ok = false, "exceção no codec ETC2: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun checarLua(): Resultado {
        // SCRIPT REAL (D2.4, v0.1.16): usa PRINT + MATH + BIT32 + STRING de fato.
        // No release minificado o R8 quebrava o bind reflexivo das libs do LuaJ
        // (Bit32Lib$Bit32LibV via Class.newInstance) — um script que só soma
        // floats PASSARIA e esconderia o bug. O valor esperado é fixo e
        // verificado: math.floor(abs(-2.5)+4.5)=7 · bit32.bor(lshift(1,4),3)=19 ·
        // string.format("%x",19)="13" (len 2) → 7+19+2 = 28; on_update soma 1
        // → 29 após o primeiro tick.
        val fonte = """
            print("diagnostico: prova do LuaJ (math+bit32+string+print)")
            diag_resultado = 0
            local function prova(a, b)
              local m = math.floor(math.abs(a) + b)
              local bits = bit32.bor(bit32.lshift(1, 4), 3)
              local txt = string.format("%x", bits)
              print("diagnostico:", txt)
              diag_resultado = m + bits + string.len(txt)
            end
            prova(-2.5, 4.5)
            on_update = function(dt) diag_resultado = diag_resultado + 1 end
        """.trimIndent()
        val rt = try {
            ScriptRuntime.criar("diag-release", fonte, orcamentoMs = 50L)
        } catch (e: Exception) {
            return Resultado("lua", ok = false, "exceção ao criar ScriptRuntime: ${e.message ?: e.javaClass.simpleName}")
        }
        if (rt == null) return Resultado("lua", ok = false, "script real não compilou (criar devolveu null)")
        val iniciou = rt.iniciar()
        if (!iniciou) {
            return Resultado("lua", ok = false, "script real não iniciou: ${rt.ultimoErro() ?: "sem erro reportado"} — bind reflexivo das libs do LuaJ é o primeiro suspeito (R8)")
        }
        val atualizou = rt.atualizar(1f / 60f)
        if (!atualizou) {
            return Resultado("lua", ok = false, "on_update falhou/desativou: ${rt.ultimoErro() ?: "sem erro reportado"}")
        }
        // Valor esperado lido do AMBIENTE — prova que math, bit32 e string
        // produziram os números CERTOS (não só que não lançaram).
        val globals = (rt as? com.kenkern.engine.core.script.LuaJScriptRuntime)?.ambiente()
        val valor = globals?.get("diag_resultado")?.toint() ?: Int.MIN_VALUE
        return if (valor == 29) {
            Resultado(
                "lua", ok = true,
                "script real executou print + math + bit32 + string (resultado 28 = 7+19+2; +1 no on_update = 29) — bind reflexivo das libs OK"
            )
        } else {
            Resultado("lua", ok = false, "resultado do script foi $valor (esperado 29) — math/bit32/string produziram números errados ou o ambiente divergiu")
        }
    }
}
