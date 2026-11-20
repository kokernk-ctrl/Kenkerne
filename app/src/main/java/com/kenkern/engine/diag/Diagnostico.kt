package com.kenkern.engine.diag

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES30
import com.kenkern.engine.assets.import.AssetLayout
import com.kenkern.engine.assets.import.FbxAmostras
import com.kenkern.engine.assets.import.FbxImportException
import com.kenkern.engine.assets.import.FbxImporter
import com.kenkern.engine.assets.import.GltfAmostras
import com.kenkern.engine.assets.import.GltfImportException
import com.kenkern.engine.assets.import.GltfImporter
import com.kenkern.engine.assets.import.ImportException
import com.kenkern.engine.assets.import.ImportSummary
import com.kenkern.engine.assets.import.KernIntegrity
import com.kenkern.engine.assets.import.ModelImporter
import com.kenkern.engine.assets.import.ResolvedorZip
import com.kenkern.engine.commands.ImportModelCommand
import com.kenkern.engine.assets.mesh.KernReader
import com.kenkern.engine.assets.mesh.KernWriter
import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.linkerf.LinkerfLink
import com.kenkern.engine.core.linkerf.LinkerfPort
import com.kenkern.engine.core.linkerf.LinkerfPortKind
import com.kenkern.engine.core.linkerf.LinkerfPortType
import com.kenkern.engine.core.math.Vec3
import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.render.CameraMode
import com.kenkern.engine.render.GlesBackend
import com.kenkern.engine.render.OrbitCamera
import kotlin.math.abs
import kotlin.math.max

/**
 * TELA DIAGNÓSTICO (Prompt Mestre v3): checagens que rodam NO APARELHO com
 * resultado APROVADO/FALHOU + motivo. Toda fase adiciona checagens aqui
 * (Fase 3 acrescenta execução de scripts Lua).
 *
 * Checagens da Fase 2:
 *  - ck       — ida e volta do formato de cena .ck (CK1);
 *  - kern     — ida e volta do formato de malha .KERN com erro máximo medido;
 *  - gltf     — importador glTF/GLB → .KERN (meta + integridade + erros claros);
 *  - fbx      — importador FBX → .KERN (merge de instâncias + integridade + erros claros);
 *  - importar — botão Importar modelo: pipeline completa com modelo embutido
 *               (layout de assets, nome único, UM Command undo/redo, zip com .bin);
 *  - picking  — matemática do raio de tela nos 2 modos de câmera;
 *  - shaders  — compilação/link de um programa de teste no driver (GLSL ES 3.00);
 *  - buffers  — alocação e liberação de VBO/VAO reais via GlesBackend;
 *  - glcaps   — versão/renderizador/extensões/compute/limites;
 *  - memoria  — classes de memória e limites do aparelho.
 *
 * Regras: nada aqui derruba o app (toda checagem é blindada); sem GL no
 * construtor (só quando [executar] roda numa thread de fundo com o [EglDiag]);
 * zero dependência de Compose (a tela só observa os resultados).
 */
class Diagnostico(private val appContext: Context) {

    private companion object {
        const val TAG = "Kenkern/Diagnostico"
    }

    enum class Status { PENDENTE, RODANDO, APROVADO, FALHOU }

    class Checagem(val id: String, val nome: String) {
        var status: Status = Status.PENDENTE
            private set
        var detalhe: String = "—"
            private set

        fun aprovado(detalhe: String) {
            status = Status.APROVADO; this.detalhe = detalhe
        }

        fun falhou(detalhe: String) {
            status = Status.FALHOU; this.detalhe = detalhe
        }

        fun rodando() {
            status = Status.RODANDO; detalhe = "executando…"
        }
    }

    fun novaLista(): List<Checagem> = listOf(
        Checagem("ck", ".ck (cena) — ida e volta"),
        Checagem("kern", ".KERN (malha) — ida e volta"),
        Checagem("gltf", "Importador glTF/GLB → .KERN (meta + integridade)"),
        Checagem("fbx", "Importador FBX → .KERN (instâncias + integridade)"),
        Checagem("importar", "Botão Importar modelo (pipeline + undo/redo + zip)"),
        Checagem("picking", "Matemática de picking (raio de tela)"),
        Checagem("shaders", "Compilação de shaders (GLSL ES)"),
        Checagem("buffers", "Alocação de buffers (VBO/VAO)"),
        Checagem("glcaps", "Capacidades GL (versão/extensões/compute)"),
        Checagem("memoria", "Limites de memória do aparelho")
    )

    /**
     * Executa TODAS as checagens, na ordem, chamando [onAtualiza] a cada mudança
     * de estado (a tela observa). Bloqueante — chamar de thread de fundo.
     * O contexto EGL é criado uma única vez para o bloco GL e liberado no fim.
     * Nunca lança.
     */
    fun executar(onAtualiza: (Checagem) -> Unit) {
        val rapidas = listOf("ck", "kern", "gltf", "fbx", "importar", "picking")
        val gl = listOf("shaders", "buffers", "glcaps")

        for (id in rapidas) rodarUma(id, onAtualiza)

        val erro = StringBuilder()
        val egl = EglDiag.criar { e -> erro.append(e) }
        if (egl != null) {
            try {
                for (id in gl) rodarUma(id, onAtualiza, egl)
            } finally {
                egl.liberar()
            }
        } else {
            val motivo = if (erro.isEmpty()) "motivo desconhecido" else erro.toString()
            for (id in gl) {
                val c = Checagem(id, nome(id))
                c.falhou("contexto EGL pbuffer não criado: $motivo")
                onAtualiza(c)
            }
        }

        rodarUma("memoria", onAtualiza)
    }

    private fun nome(id: String): String = novaLista().first { it.id == id }.nome

    private fun rodarUma(id: String, onAtualiza: (Checagem) -> Unit, egl: EglDiag? = null) {
        val c = Checagem(id, nome(id))
        c.rodando()
        onAtualiza(c)
        runCatching {
            when (id) {
                "ck" -> checarCk(c)
                "kern" -> checarKern(c)
                "gltf" -> checarGltf(c)
                "fbx" -> checarFbx(c)
                "importar" -> checarImportar(c)
                "picking" -> checarPicking(c)
                "shaders" -> checarShaders(c)
                "buffers" -> checarBuffers(c)
                "glcaps" -> checarGlCaps(c)
                "memoria" -> checarMemoria(c)
            }
        }.onFailure {
            c.falhou("exceção: ${it.message ?: it.javaClass.simpleName}")
            KenkernLog.e(TAG, "checagem '$id' lançou exceção", it)
        }
        onAtualiza(c)
    }

    // ---------------------------------------------------------------- .ck

    private fun checarCk(c: Checagem) {
        val scene = Scene("Diag")
        val mundo = scene.createRootNick("Mundo")
        val cubo = scene.createNick("Cubo", mundo)
        val spin = NickClassInstance("Spin")
        spin.fields["speed"] = NickValue.FloatValue(72.5f)
        cubo.addClassInstance(spin)
        val filho = scene.createNick("Filho", mundo)
        filho.transform.setLocalPosition(3f, -2.5f, 0.5f)
        val dados = NickClassInstance("Dados")
        dados.fields["n"] = NickValue.IntValue(7)
        dados.fields["ok"] = NickValue.BoolValue(true)
        dados.fields["s"] = NickValue.StringValue("diagnóstico \"rígido\"")
        dados.fields["v"] = NickValue.Vec3Value(1f, 2f, 3f)
        filho.addClassInstance(dados)
        val pSaida = LinkerfPort(
            com.kenkern.engine.core.ReferenceId.generate(),
            com.kenkern.engine.core.ReferenceId.generate(),
            "saiu", LinkerfPortKind.EVENT_OUT, LinkerfPortType.TRIGGER
        )
        val pEntrada = LinkerfPort(
            com.kenkern.engine.core.ReferenceId.generate(),
            com.kenkern.engine.core.ReferenceId.generate(),
            "chegou", LinkerfPortKind.EVENT_IN, LinkerfPortType.TRIGGER
        )
        scene.linkerf.addPort(pSaida)
        scene.linkerf.addPort(pEntrada)
        scene.linkerf.addLinkDirect(LinkerfLink(com.kenkern.engine.core.ReferenceId.generate(), pSaida.id, pEntrada.id))

        val texto = CkFormat.writeToString(scene)
        val volta = CkFormat.parseToNewScene(texto, "fallback")
            ?: throw IllegalStateException("parse retornou null")

        val problemas = ArrayList<String>(4)
        if (volta.roots.size != 1 || volta.roots[0].children.size != 2) problemas.add("hierarquia")
        val cuboV = volta.roots[0].children.firstOrNull { it.name == "Cubo" }
            ?: throw IllegalStateException("Cubo sumiu no round-trip")
        val spinV = cuboV.findClassInstance("Spin")
        if (spinV == null || (spinV.fields["speed"] as? NickValue.FloatValue)?.value != 72.5f) problemas.add("campo FLOAT")
        val filhoV = volta.roots[0].children.firstOrNull { it.name == "Filho" }
        if (filhoV == null || abs(filhoV.transform.position.y - -2.5f) > 1e-4f) problemas.add("transform")
        val dadosV = filhoV?.findClassInstance("Dados")
        if (dadosV == null ||
            (dadosV.fields["n"] as? NickValue.IntValue)?.value != 7 ||
            (dadosV.fields["ok"] as? NickValue.BoolValue)?.value != true ||
            (dadosV.fields["s"] as? NickValue.StringValue)?.value != "diagnóstico \"rígido\"" ||
            (dadosV.fields["v"] as? NickValue.Vec3Value)?.z != 3f
        ) problemas.add("tipos de campo")
        if (volta.linkerf.allPorts().size != 2 || volta.linkerf.allLinks().size != 1) problemas.add("LINKERF")

        if (problemas.isEmpty()) {
            c.aprovado(
                "3 Nicks · 2 classes · 4 tipos de campo · LINKERF 2 portas/1 link — " +
                    "ida e volta exata (${texto.length} bytes)"
            )
        } else {
            c.falhou("divergência no round-trip: ${problemas.joinToString()}")
        }
    }

    // ---------------------------------------------------------------- .KERN

    private fun checarKern(c: Checagem) {
        // Pirâmide de 4 vértices/12 índices: menor malha válida com triângulos.
        val b = KernWriter.Builder()
        b.vertex(0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f)
        b.vertex(1f, 0f, 0f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, 0f)
        b.vertex(1f, 0f, 1f, 0f, -1f, 0f, 1f, 1f, 1f, 0f, 0f)
        b.vertex(0.5f, 1f, 0.5f, 0.5f, 1f, 0.5f, 0.5f, 0.5f, 1f, 0f, 0f)
        b.triangle(0, 2, 1)
        b.triangle(0, 1, 3)
        b.triangle(1, 2, 3)
        b.triangle(2, 0, 3)
        val original = b.build()
        val lida = KernReader.read(KernWriter.write(original))

        if (lida.vertexCount != 4 || lida.indices.size != 12) {
            c.falhou("contagens erradas: ${lida.vertexCount} vértices / ${lida.indices.size} índices (esperado 4/12)")
            return
        }
        val span = max(
            original.boundsMax.x - original.boundsMin.x,
            max(original.boundsMax.y - original.boundsMin.y, original.boundsMax.z - original.boundsMin.z)
        )
        val tolerancia = span / 65535f
        var erroMax = 0f
        for (i in original.positions.indices) {
            erroMax = max(erroMax, abs(original.positions[i] - lida.positions[i]))
        }
        for (i in original.indices.indices) {
            if (original.indices[i] != lida.indices[i]) {
                c.falhou("índice $i divergiu: ${original.indices[i]} vs ${lida.indices[i]}")
                return
            }
        }
        if (erroMax > tolerancia) {
            c.falhou("erro máximo de posição $erroMax > tolerância $tolerancia")
        } else {
            c.aprovado(
                "4 vértices · 12 índices · índices exatos · erro máx de posição " +
                    "%.1e (tolerância %.1e)".format(java.util.Locale.US, erroMax, tolerancia)
            )
        }
    }

    // ---------------------------------------------------------------- glTF (F2.7)

    private fun checarGltf(c: Checagem) {
        // 1) Quad GLB completo (POSITION+NORMAL+UV+índices u16)
        val r1 = try {
            GltfImporter.importar(GltfAmostras.quadGlb())
        } catch (e: Exception) {
            c.falhou("quad GLB: ${e.message}")
            return
        }
        if (r1.mesh.vertexCount != 4 || r1.mesh.indices.size != 6) {
            c.falhou("quad GLB: ${r1.mesh.vertexCount} vértices / ${r1.mesh.indices.size} índices (esperado 4/6)")
            return
        }
        val p1 = KernIntegrity.verificar(r1.mesh, r1.bytesKern)
        if (p1.isNotEmpty()) {
            c.falhou("quad GLB integridade: ${p1.joinToString("; ")}")
            return
        }

        // 2) Cubo em 2 nodes com transform (TRS/quaternion + herança) → merge
        val r2 = try {
            GltfImporter.importar(GltfAmostras.cuboComTransformGlb())
        } catch (e: Exception) {
            c.falhou("cubo com transform: ${e.message}")
            return
        }
        if (r2.mesh.vertexCount != 16 || r2.mesh.indices.size != 72) {
            c.falhou("cubo com transform: ${r2.mesh.vertexCount} vértices / ${r2.mesh.indices.size} índices (esperado 16/72 — merge de 2 instâncias)")
            return
        }
        val p2 = KernIntegrity.verificar(r2.mesh, r2.bytesKern)
        if (p2.isNotEmpty()) {
            c.falhou("cubo com transform integridade: ${p2.joinToString("; ")}")
            return
        }

        // 3) Erros CLAROS (nunca silenciosos — regra da engine)
        val magicOk = try {
            GltfImporter.importar(GltfAmostras.magicQuebrado())
            false
        } catch (e: GltfImportException) {
            true
        }
        if (!magicOk) {
            c.falhou("arquivo com magic quebrado NÃO gerou erro claro")
            return
        }

        c.aprovado(
            "GLB quad + cubo com transform (merge 2 instâncias) importados, integridade OK · " +
                r1.report.resumo() + " · erros claros OK"
        )
    }

    // ---------------------------------------------------------------- fbx

    private fun checarFbx(c: Checagem) {
        // 1) Quad FBX (2 triângulos expandidos = 6 vértices; normais Direct + UV IndexToDirect)
        val r1 = try {
            FbxImporter.importar(FbxAmostras.quadFbx())
        } catch (e: Exception) {
            c.falhou("quad FBX: ${e.message}")
            return
        }
        if (r1.mesh.vertexCount != 6 || r1.mesh.indices.size != 6) {
            c.falhou("quad FBX: ${r1.mesh.vertexCount} vértices / ${r1.mesh.indices.size} índices (esperado 6/6)")
            return
        }
        val p1 = KernIntegrity.verificar(r1.mesh, r1.bytesKern)
        if (p1.isNotEmpty()) {
            c.falhou("quad FBX integridade: ${p1.joinToString("; ")}")
            return
        }

        // 2) Cubo em 6 polígonos de quad compartilhado por 2 Models (TRS em graus)
        //    → merge de instâncias: 24 cantos × 2 = 48 vértices, 36 índices × 2 = 72
        val r2 = try {
            FbxImporter.importar(FbxAmostras.cuboCompartilhadoFbx())
        } catch (e: Exception) {
            c.falhou("cubo compartilhado: ${e.message}")
            return
        }
        if (r2.mesh.vertexCount != 48 || r2.mesh.indices.size != 72) {
            c.falhou("cubo compartilhado: ${r2.mesh.vertexCount} vértices / ${r2.mesh.indices.size} índices (esperado 48/72)")
            return
        }
        val p2 = KernIntegrity.verificar(r2.mesh, r2.bytesKern)
        if (p2.isNotEmpty()) {
            c.falhou("cubo compartilhado integridade: ${p2.joinToString("; ")}")
            return
        }

        // 3) Erros CLAROS (nunca silenciosos — regra da engine)
        val asciiOk = try {
            FbxImporter.importar(FbxAmostras.asciiFbx())
            false
        } catch (e: FbxImportException) {
            true
        }
        if (!asciiOk) {
            c.falhou("FBX ASCII sem Objects NÃO gerou erro claro")
            return
        }

        // 4) FBX ASCII COMPLETO (ETAPA B.2): mesmo quad do binário, escrito em texto
        val asciiQuad = try {
            FbxImporter.importar(FbxAmostras.quadAsciiFbx())
        } catch (e: Exception) {
            c.falhou("FBX ASCII completo: ${e.message}")
            return
        }
        if (asciiQuad.mesh.vertexCount != 6 || asciiQuad.mesh.indices.size != 6) {
            c.falhou(
                "FBX ASCII completo: ${asciiQuad.mesh.vertexCount} vértices / " +
                    "${asciiQuad.mesh.indices.size} índices (esperado 6/6)"
            )
            return
        }

        // 5) FBX ASCII com MATERIAL (ETAPA B.3): tabela + grupo + cor do DiffuseColor
        val asciiMat = try {
            FbxImporter.importar(FbxAmostras.quadAsciiComMaterialFbx())
        } catch (e: Exception) {
            c.falhou("FBX ASCII com material: ${e.message}")
            return
        }
        if (asciiMat.mesh.materiais.size != 1 || asciiMat.mesh.gruposMaterial.size != 1) {
            c.falhou(
                "FBX ASCII material: ${asciiMat.mesh.materiais.size} materiais / " +
                    "${asciiMat.mesh.gruposMaterial.size} grupos (esperado 1/1)"
            )
            return
        }

        c.aprovado(
            "FBX quad + cubo compartilhado (merge 2 instâncias, TRS em graus) importados, " +
                "integridade OK · " + r2.report.resumo() + " · erros claros OK · " +
                "FBX ASCII (B.2) quad 6/6 + material (B.3) OK"
        )
    }

    // ---------------------------------------------------------------- importar (ETAPA A)

    /**
     * CHECAGEM "importar": pipeline COMPLETA do botão Importar com um modelo
     * EMBUTIDO (sem SAF): ModelImporter (GLB) + layout de assets com nome
     * único + UM Command undo/redo (Nick sai, arquivos vão para a lixeira e
     * voltam) + glTF externo dentro de .zip (resolver com zip-slip bloqueado).
     */
    private fun checarImportar(c: Checagem) {
        // Pasta temporária que simula a raiz de um projeto (só nesta checagem).
        val raiz = java.io.File(appContext.cacheDir, "diag-import-${System.nanoTime()}")
        try {
            AssetLayout.criarPastas(raiz)

            // 1) Importação GLB embutida (pipeline completa, etapas + integridade)
            val summary: ImportSummary = try {
                ModelImporter.importarModelo(
                    "diag-quad.glb", GltfAmostras.quadGlb(), ModelImporter.Opcoes(true, 2f)
                )
            } catch (e: Exception) {
                c.falhou("GLB embutido: ${e.message}")
                return
            }
            if (!summary.integridadeOk) {
                c.falhou("GLB embutido integridade: ${summary.problemasIntegridade.joinToString("; ")}")
                return
            }
            if (summary.bytesKernData.isEmpty() || summary.vertices != 4) {
                c.falhou("GLB embutido: ${summary.vertices} vértices / ${summary.bytesKern} bytes (esperado 4 vértices)")
                return
            }

            // 2) Layout de assets: nome único (2ª cópia vira -2, nunca sobrescreve)
            val dirOrigem = java.io.File(raiz, AssetLayout.DIR_ORIGEM)
            val n1 = AssetLayout.nomeUnico(dirOrigem, "diag-quad", "glb")
            java.io.File(dirOrigem, n1).writeBytes(GltfAmostras.quadGlb())
            val n2 = AssetLayout.nomeUnico(dirOrigem, "diag-quad", "glb")
            if (n1 == n2) {
                c.falhou("nome único falhou: '$n1' duas vezes (colisão sobrescreve)")
                return
            }
            if (AssetLayout.caminhoRelativoSeguro("../fuga.bin") != null ||
                AssetLayout.caminhoRelativoSeguro("/absoluto.bin") != null
            ) {
                c.falhou("caminhoRelativoSeguro aceitou '..' ou caminho absoluto (zip-slip)")
                return
            }

            // 3) UM Command: cria Nick com classe Malha; undo remove Nick + recolhe
            //    arquivos; redo devolve tudo (A.3)
            val scene = Scene("Diag-Import")
            val mundo = scene.createRootNick("Mundo")
            val kernNome = AssetLayout.nomeUnico(java.io.File(raiz, AssetLayout.DIR_MODELOS), "diag-quad", "KERN")
            val kernRel = "${AssetLayout.DIR_MODELOS}/$kernNome"
            java.io.File(raiz, kernRel).writeBytes(summary.bytesKernData)
            val classe = ModelImporter.criarClasseMalha(kernRel, summary.aabbMin, summary.aabbMax, summary.escalaSugerida)
            val cmd = ImportModelCommand(
                scene, mundo.id, "diag-quad", raiz,
                listOf(kernRel, "${AssetLayout.DIR_ORIGEM}/$n1"), classe
            )
            cmd.execute()
            val nick = scene.findNickByNameAnywhere("diag-quad")
            if (nick == null || !com.kenkern.engine.core.MalhaNick.temMalha(nick)) {
                c.falhou("Command não criou o Nick com a classe Malha")
                return
            }
            cmd.undo()
            if (scene.roots[0].children.isNotEmpty()) {
                c.falhou("undo não removeu o Nick importado")
                return
            }
            val kernApagado = !java.io.File(raiz, kernRel).exists() &&
                java.io.File(java.io.File(raiz, "assets/.lixeira-import"), kernRel).exists()
            if (!kernApagado) {
                c.falhou("undo não recolheu o .KERN para a lixeira de importação")
                return
            }
            cmd.execute()
            val voltou = java.io.File(raiz, kernRel).exists() &&
                scene.roots[0].children.any { it.name == "diag-quad" }
            if (!voltou) {
                c.falhou("redo não devolveu o .KERN e o Nick")
                return
            }

            // 4) glTF com .bin externo dentro de .zip (resolver + zip-slip B.1)
            val zipModelo = try {
                val zipBytes = java.io.ByteArrayOutputStream()
                java.util.zip.ZipOutputStream(zipBytes).use { zout ->
                    zout.putNextEntry(java.util.zip.ZipEntry("modelo/quad.gltf"))
                    zout.write(GltfAmostras.quadGltfExterno())
                    zout.closeEntry()
                    zout.putNextEntry(java.util.zip.ZipEntry("modelo/quad.bin"))
                    zout.write(GltfAmostras.quadBinExterno())
                    zout.closeEntry()
                }
                ModelImporter.importarModelo("quad.zip", zipBytes.toByteArray())
            } catch (e: Exception) {
                c.falhou("glTF externo no zip: ${e.message}")
                return
            }
            if (!zipModelo.integridadeOk || zipModelo.vertices != 4) {
                c.falhou("glTF externo no zip: ${zipModelo.vertices} vértices / ${zipModelo.problemasIntegridade.joinToString("; ")}")
                return
            }

            c.aprovado(
                "GLB embutido (4 v, integridade OK, pico %.0f MB, %d ms) · nome único + zip-slip bloqueado · " +
                    "Command undo/redo com lixeira OK · glTF com .bin externo via .zip OK".format(
                        java.util.Locale.US, summary.picoMemoriaMb, summary.tempoMs
                    )
            )
        } finally {
            raiz.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------- picking

    private fun checarPicking(c: Checagem) {
        val cam = OrbitCamera()
        val o = Vec3()
        val d = Vec3()
        val fwd = Vec3()
        val problemas = ArrayList<String>(3)

        // Raio pelo CENTRO da tela == direção de olhar (base do toque-para-mundo).
        cam.mode = CameraMode.ORBITAL
        cam.forwardVector(fwd)
        cam.screenRay(0f, 0f, 16f / 9f, o, d)
        if (o.distanceTo(Vec3().set(cam.target).addScaled(fwd, cam.distance)) > 0.01f) problemas.add("origem orbital")
        if (abs(d.x - fwd.x) > 1e-3f || abs(d.y - fwd.y) > 1e-3f || abs(d.z - fwd.z) > 1e-3f) {
            problemas.add("direção orbital")
        }

        cam.mode = CameraMode.FLIGHT
        cam.flightPosition.set(3f, 2f, 5f)
        cam.flightYawDegrees = -40f
        cam.flightPitchDegrees = 12f
        cam.forwardVector(fwd)
        cam.screenRay(0f, 0f, 9f / 16f, o, d)
        if (o.distanceTo(cam.flightPosition) > 1e-4f) problemas.add("origem voo")
        if (abs(d.x - fwd.x) > 1e-3f || abs(d.y - fwd.y) > 1e-3f || abs(d.z - fwd.z) > 1e-3f) {
            problemas.add("direção voo")
        }

        // Inversão de Y (ndcY = 1 - 2y/h): raio do TOCO tem componente Y maior.
        val topo = Vec3()
        val base = Vec3()
        cam.screenRay(0f, 1f, 1f, o, topo)
        cam.screenRay(0f, -1f, 1f, o, base)
        if (topo.y <= base.y) problemas.add("inversão de Y")

        if (problemas.isEmpty()) {
            c.aprovado("raio do centro = olhar em ORBITAL e VOO; topo/base e Y invertidos corretamente")
        } else {
            c.falhou("divergência: ${problemas.joinToString()}")
        }
    }

    // ---------------------------------------------------------------- GL: shaders

    private fun checarShaders(c: Checagem) {
        val vs = """
            #version 300 es
            in vec3 aPos;
            void main() { gl_Position = vec4(aPos, 1.0); }
        """.trimIndent()
        val fs = """
            #version 300 es
            precision mediump float;
            out vec4 cor;
            void main() { cor = vec4(0.5, 0.75, 1.0, 1.0); }
        """.trimIndent()

        var vShader = 0
        var fShader = 0
        var programa = 0
        try {
            vShader = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
            GLES30.glShaderSource(vShader, vs)
            GLES30.glCompileShader(vShader)
            val okV = IntArray(1)
            GLES30.glGetShaderiv(vShader, GLES30.GL_COMPILE_STATUS, okV, 0)
            if (okV[0] == 0) {
                c.falhou("vertex shader: " + GLES30.glGetShaderInfoLog(vShader).trim().take(200))
                return
            }

            fShader = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
            GLES30.glShaderSource(fShader, fs)
            GLES30.glCompileShader(fShader)
            val okF = IntArray(1)
            GLES30.glGetShaderiv(fShader, GLES30.GL_COMPILE_STATUS, okF, 0)
            if (okF[0] == 0) {
                c.falhou("fragment shader: " + GLES30.glGetShaderInfoLog(fShader).trim().take(200))
                return
            }

            programa = GLES30.glCreateProgram()
            GLES30.glAttachShader(programa, vShader)
            GLES30.glAttachShader(programa, fShader)
            GLES30.glLinkProgram(programa)
            val okL = IntArray(1)
            GLES30.glGetProgramiv(programa, GLES30.GL_LINK_STATUS, okL, 0)
            if (okL[0] == 0) {
                c.falhou("link: " + GLES30.glGetProgramInfoLog(programa).trim().take(200))
                return
            }

            val glsl = GLES30.glGetString(GLES30.GL_SHADING_LANGUAGE_VERSION) ?: "?"
            val renderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: "?"
            c.aprovado("programa de teste compilou e linkou · GLSL $glsl · $renderer")
        } finally {
            if (programa != 0) GLES30.glDeleteProgram(programa)
            if (vShader != 0) GLES30.glDeleteShader(vShader)
            if (fShader != 0) GLES30.glDeleteShader(fShader)
        }
    }

    // ---------------------------------------------------------------- GL: buffers

    private fun checarBuffers(c: Checagem) {
        // MeshData mínima válida (1 triângulo) com normais preenchidas.
        val malha = MeshData(
            floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
            floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f),
            intArrayOf(0, 1, 2),
            Vec3(0f, 0f, 0f),
            Vec3(1f, 1f, 0f)
        )
        val backend = GlesBackend()
        val handle = backend.createMesh(malha)
        try {
            if (handle == 0) {
                c.falhou("createMesh devolveu handle 0 (alocação de VBO/VAO falhou)")
            } else {
                c.aprovado("VBO + VAO de 3 vértices/3 índices alocados (handle $handle) e liberados")
            }
        } finally {
            if (handle != 0) backend.destroyMesh(handle)
        }
    }

    // ---------------------------------------------------------------- GL: capacidades

    private fun checarGlCaps(c: Checagem) {
        val versao = GLES30.glGetString(GLES30.GL_VERSION) ?: "?"
        val renderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: "?"

        // "OpenGL ES 3.2 ..." → "3.2"
        val m = Regex("""OpenGL ES (\d+)\.(\d+)""").find(versao)
        if (m == null) {
            c.falhou("versão GL ilegível: \"$versao\"")
            return
        }
        val maior = m.groupValues[1].toInt()
        val menor = m.groupValues[2].toInt()

        val extensoes = GLES30.glGetString(GLES30.GL_EXTENSIONS)?.split(' ')?.size ?: 0
        val maxTex = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        val maxVerts = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_RENDERBUFFER_SIZE, maxVerts, 0)

        val compute = maior > 3 || (maior == 3 && menor >= 1)
        val resumo = buildString {
            append("OpenGL ES $maior.$menor · $renderer · $extensoes extensões · ")
            append("tex máx ${maxTex[0]}px · renderbuffer máx ${maxVerts[0]}px · ")
            append(if (compute) "compute disponível" else "sem compute (KENA usará fallback)")
        }
        if (maior >= 3) c.aprovado(resumo) else c.falhou("engine exige OpenGL ES 3.0+: \"$versao\"")
    }

    // ---------------------------------------------------------------- memória

    private fun checarMemoria(c: Checagem) {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am == null) {
            c.falhou("ActivityManager indisponível")
            return
        }
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val mb = 1024L * 1024L
        val detalhe = buildString {
            append("heap ${am.memoryClass} MB (large ${am.largeMemoryClass} MB) · ")
            append("livre ${info.availMem / mb} de ${info.totalMem / mb} MB · ")
            append(if (am.isLowRamDevice) "aparelho low-RAM" else "aparelho normal")
            append(if (info.lowMemory) " · AVISO: sistema em memória baixa" else "")
        }
        if (info.lowMemory && info.availMem < 64L * mb) {
            c.falhou("memória crítica: $detalhe")
        } else {
            c.aprovado(detalhe)
        }
    }
}
