package com.kenkern.engine.ui.editor

/**
 * R2 (v0.1.29) — TRECHOS DO "VERIFICAR": fonte ÚNICA (a checagem "editor" do
 * Diagnóstico e os testes JVM leem DAQUI — nada de cópia). 5 quebrados + 3
 * válidos, como a tarefa pede.
 *
 * CAUSA RAIZ (v0.1.28): a checagem chamava de QUEBRADO o trecho
 * "local x =\nprint(x)" — que é Lua VÁLIDO (`local x =` com a expressão
 * continuando na linha seguinte compila: x recebe o resultado de print(x)).
 * A premissa falsa reprovava o Verificar à toa ("verificar: código quebrado
 * passou" no aparelho). O trecho antigo aparece em [VALIDOS] — a história
 * fica registrada como contrato executável.
 *
 * O Verificar usa o COMPILADOR do LuaJ (LuaC.instance.compile) e NADA
 * EXECUTA — sintaxe pura, sem efeitos.
 */
object TrechosVerificar {

    val QUEBRADOS: List<Pair<String, String>> = listOf(
        "end faltando" to "function f()\n  return 1\n",
        "string aberta" to "local s = \"sem fechar",
        "igual solto" to "= 1",
        "função sem nome" to "function ()\n  return 1\nend",
        "caractere inválido" to "local x = 1 § 2"
    )

    val VALIDOS: List<Pair<String, String>> = listOf(
        // o trecho que a checagem antiga chamava de quebrado — é válido.
        "atribuição multiline (o falso quebrado da v0.1.28)" to "local x =\nprint(x)",
        "função completa" to "function f()\n  return 1\nend",
        "string e retorno" to "local s = \"ok\"\nreturn s"
    )
}
