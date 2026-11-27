package com.kenkern.engine.diag

import com.kenkern.engine.ui.editor.TrechosVerificar
import com.kenkern.engine.ui.screens.verificarLua
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 (v0.1.29) — PREMISSA DA CHECAGEM "editor" (vermelho na v0.1.28 → verde
 * aqui). O trecho "local x =\nprint(x)" que a checagem antiga chamava de
 * QUEBRADO é Lua VÁLIDO (`local x =` com a expressão continuando na linha
 * seguinte compila: x recebe o resultado de print(x)) — a premissa falsa
 * reprovava o Verificar à toa ("verificar: código quebrado passou" no
 * aparelho). Registro executável: o falso-quebrado agora mora em
 * [TrechosVerificar.VALIDOS] e compila; nenhum trecho VÁLIDO pode estar
 * classificado como QUEBRADO.
 */
class R2PremissaChecagemEditorTest {

    /** O falso-quebrado da v0.1.28 — exatamente como checarEditor usava. */
    private val falsoQuebrado = "local x =\nprint(x)"

    @Test
    fun `o falso-quebrado da v0_1_28 é Lua válido e está em VALIDOS`() {
        assertNull("compila no LuaJ (era a premissa falsa da checagem)", verificarLua(falsoQuebrado))
        assertTrue(
            "o trecho tem que estar classificado em VALIDOS (nunca em QUEBRADOS)",
            TrechosVerificar.VALIDOS.any { it.second == falsoQuebrado }
        )
    }
}
