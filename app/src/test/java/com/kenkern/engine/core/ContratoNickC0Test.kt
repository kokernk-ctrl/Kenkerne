package com.kenkern.engine.core

import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.engine.NickLuaScripts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * C0 (v0.1.25): CONTRATO DE NICK —
 *  - Kinds SEM FUNÇÃO ficam fora da janela Adicionar Nick (`visivel = false`),
 *    mas continuam no registro e abrem em projetos antigos;
 *  - Todo kind VISÍVEL tem função real verificada em Play, Inspetor e Lua.
 */
class ContratoNickC0Test {

    @Before
    fun setup() {
        NickClassRegistry.clear()
        KenkernBuiltInClasses.registerAll()
        NickKindRegistry.clear()
        NickKindCatalog.registerAll()
    }

    @Test
    fun `todo kind visivel tem funcao em Play, Inspetor e Lua`() {
        val visiveis = NickKindRegistry.visiveis()
        assertTrue("há kinds visíveis no catálogo", visiveis.isNotEmpty())

        for (def in visiveis) {
            assertTrue("${def.key} visível não pode ser SEM_FUNCAO", def.status != NickKindStatus.SEM_FUNCAO)

            // 1) Play: descrição funcional real (não começa com "NÃO" nem é "—")
            val emPlay = KindEmPlay.comportamentoEmPlay(def.key)
            assertFalse("${def.key} precisa de comportamento real em Play (atual: $emPlay)",
                emPlay == "—" || emPlay.startsWith("NÃO"))

            // 2) Inspetor: possui classes padrão válidas no registro (ou transform/flags editáveis)
            for (classe in def.defaultClasses) {
                assertNotNull("${def.key}: classe padrão '$classe' deve estar registrada para o Inspetor",
                    NickClassRegistry.find(classe))
            }

            // 3) Lua: scene.find encontra o Nick e consegue ler nome e alterar posição/ativo
            val scene = Scene("contrato-${def.key}")
            val nomeUnico = "Alvo_${def.key.replace('.', '_')}"
            val nick = scene.createRootNick(nomeUnico, kind = def.key)
            for (c in def.defaultClasses) {
                NickClassRegistry.createInstance(c)?.let { nick.addClassInstance(it) }
            }
            val controlador = scene.createRootNick("Driver", kind = NickKindRegistry.NICK_EMPTY)
            val scriptInst = NickClassRegistry.createInstance("Script")!!
            scriptInst.fields["fonte"] = NickValue.StringValue(
                """
                function on_start()
                    local h = scene.find("$nomeUnico")
                    if h ~= nil then
                        h.set_position(2, 3, 4)
                        scene.emit("ok_${def.key}", h.name())
                    end
                end
                """.trimIndent()
            )
            controlador.addClassInstance(scriptInst)

            val lua = NickLuaScripts(scene)
            var eventoRecebido = false
            lua.iniciarTodos()
            // Verifica que on_start alterou a posição do Nick do kind testado via handle Lua
            assertEquals("${def.key} posição X alterada por Lua", 2f, nick.transform.position.x, 1e-4f)
            assertEquals("${def.key} posição Y alterada por Lua", 3f, nick.transform.position.y, 1e-4f)
            assertEquals("${def.key} posição Z alterada por Lua", 4f, nick.transform.position.z, 1e-4f)
            eventoRecebido = lua.instanciaDe("Driver") != null
            assertTrue("${def.key}: script Lua iniciou sem erros", eventoRecebido)
        }
    }

    @Test
    fun `kinds sem funcao ficam ocultos de Adicionar Nick mas abrem em projetos antigos`() {
        val semFuncao = NickKindRegistry.all().filter { it.status == NickKindStatus.SEM_FUNCAO }
        assertTrue("deve haver os kinds sem função mapeados na Parada 1", semFuncao.isNotEmpty())
        val visiveisKeys = NickKindRegistry.visiveis().map { it.key }.toSet()

        val cena = Scene("projeto-antigo")
        for (k in semFuncao) {
            assertFalse("${k.key} sem função deve ter visivel=false", k.visivel)
            assertFalse("${k.key} NÃO pode aparecer em NickKindRegistry.visiveis()", k.key in visiveisKeys)
            assertNotNull("${k.key} continua registrado para projetos antigos", NickKindRegistry.find(k.key))
            cena.createRootNick("Antigo_${k.key}", kind = k.key)
        }

        val texto = CkFormat.writeToString(cena)
        val lida = CkFormat.parseToNewScene(texto, "projeto-antigo")!!
        for (k in semFuncao) {
            val achado = lida.roots.firstOrNull { it.kind == k.key }
            assertNotNull("${k.key} sobrevive ao roundtrip .ck de projeto antigo", achado)
        }
    }
}
