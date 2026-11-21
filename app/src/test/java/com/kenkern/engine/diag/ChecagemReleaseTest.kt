package com.kenkern.engine.diag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes JVM da fumaça do release minificado (auditoria V1.5): os MESMOS
 * passos que o Diagnóstico roda no aparelho, contra o código não minificado
 * do CI. Se um passo falha aqui, a checagem "release" também falha no
 * aparelho — e qualquer classe que o R8 remover do APK release quebra a
 * checagem "classes" (Class.forName).
 *
 * Nota honesta: os testes JVM NÃO passam pelo R8 (unit tests rodam sobre as
 * classes compiladas) — a prova definitiva continua sendo abrir o Diagnóstico
 * no APK release (TESTES_PENDENTES.md).
 */
class ChecagemReleaseTest {

    @Test
    fun todos_os_passos_da_fumaca_passam() {
        val resultados = ChecagemRelease.executar()
        val nomes = resultados.map { it.passo }
        assertTrue(
            "esperava 6 passos (classes, gltf, fbx, kimg, etc2, lua), veio: $nomes",
            nomes == listOf("classes", "gltf", "fbx", "kimg", "etc2", "lua")
        )
        val falhas = resultados.filter { !it.ok }
        assertTrue(
            "passos com falha: " + falhas.joinToString("; ") { "${it.passo}: ${it.detalhe}" },
            falhas.isEmpty()
        )
    }

    @Test
    fun classes_criticas_estao_resolvidas_por_nome() {
        // O mesmo forName que o aparelho faz — se alguém renomear/mover uma
        // classe crítica sem atualizar a lista, o teste aponta qual.
        for (nome in ChecagemRelease.classesCriticas) {
            try {
                Class.forName(nome)
            } catch (e: ClassNotFoundException) {
                assertFalse("classe crítica ausente: $nome", true)
            }
        }
        assertTrue(ChecagemRelease.classesCriticas.size >= 8)
    }

    @Test
    fun resumo_consolida_ok_e_primeiro_erro() {
        val ok = listOf(ChecagemRelease.Resultado("a", true, "tudo certo"))
        val (nOk, erro) = ChecagemRelease.resumo(ok)
        assertTrue(nOk == 1 && erro == "—")

        val misto = listOf(
            ChecagemRelease.Resultado("a", true, "x"),
            ChecagemRelease.Resultado("b", false, "quebrou porque sim")
        )
        val (nOk2, erro2) = ChecagemRelease.resumo(misto)
        assertTrue(nOk2 == 1 && erro2 == "quebrou porque sim")
    }
}
