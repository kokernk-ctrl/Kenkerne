package com.kenkern.engine.render

import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * L1 (v0.1.28) — luz do LUME nasceu VERMELHA aqui: o FRAGMENT SHADER da malha
 * usava `vTangent` (linha do normal map T0.4) SEM declarar `in vec3 vTangent;`
 * — o shader NÃO COMPILA em nenhum driver real e o GlesBackend silenciosamente
 * caía no FALLBACK de cor sólida (GlesBackend.kt initialize: buildProgram 0 →
 * fallback). Sintoma no aparelho (v0.1.27, checagem "luz"): pixel (154,154,154)
 * = uColor #9A9A9A PURA em TODOS os cenários (pontual ligada/desligada,
 * direcional ligada/desligada) — NENHUMA luz afetava o pixel.
 *
 * A JVM não tem driver GL — a prova é ESTÁTICA: este teste valida as FONTES
 * GLSL de TODOS os shaders do GlesBackend (lidas por reflexão, sem GL):
 * todo identificador usado no corpo tem de estar DECLARADO (uniform/in/out/
 * const/local/parâmetro/nome de função) ou ser keyword/builtin do GLSL ES.
 *
 * Nasceu VERMELHO: `vTangent` usado e não declarado → falhou ANTES da
 * correção. Fica VERDE com `in vec3 vTangent;` no fragment shader.
 */
class ShaderGlslFonteTest {

    /** Tipos GLSL ES 3.00 (identificador logo após um tipo = declaração). */
    private val tipos = setOf(
        "float", "int", "bool", "void", "uint", "double",
        "vec2", "vec3", "vec4",
        "ivec2", "ivec3", "ivec4",
        "bvec2", "bvec3", "bvec4",
        "uvec2", "uvec3", "uvec4",
        "dvec2", "dvec3", "dvec4",
        "mat2", "mat3", "mat4",
        "mat2x3", "mat3x2", "mat2x4", "mat4x2", "mat3x4", "mat4x3",
        "sampler2D", "samplerCube", "sampler3D",
        "sampler2DShadow", "samplerCubeShadow", "sampler2DArray",
        "struct"
    )

    /** Keywords da linguagem (não são identificadores de dados). */
    private val keywords = setOf(
        "uniform", "const", "attribute", "varying",
        "in", "out", "inout",
        "if", "else", "for", "while", "do", "switch", "case", "default",
        "return", "break", "continue", "discard",
        "true", "false",
        "precision", "highp", "mediump", "lowp",
        "layout", "location", "flat", "smooth", "noperspective", "centroid",
        "invariant", "precise", "coherent", "volatile", "restrict", "readonly",
        "writeonly", "buffer", "shared", "patch", "sample", "subroutine"
    )

    /** Saídas/builtins do GLSL ES 3.00 usados nas fontes. */
    private val builtins = setOf(
        "gl_Position", "gl_FragColor", "gl_FragDepth", "gl_PointSize",
        "gl_FrontFacing", "gl_FragCoord", "gl_VertexID", "gl_InstanceID",
        "texture", "texture2D", "textureProj", "texelFetch",
        "normalize", "dot", "cross", "mix", "clamp", "max", "min",
        "pow", "sqrt", "inversesqrt", "abs", "floor", "ceil", "fract", "mod",
        "sign", "exp", "exp2", "log", "log2",
        "sin", "cos", "tan", "asin", "acos", "atan",
        "length", "distance", "smoothstep", "step",
        "reflect", "refract", "faceforward",
        "fwidth", "dFdx", "dFdy",
        "degrees", "radians",
        "not", "any", "all", "isnan", "isinf",
        "equal", "notEqual", "lessThan", "greaterThan",
        "lessThanEqual", "greaterThanEqual",
        "matrixCompMult", "outerProduct", "transpose", "inverse",
        "main"
    )

    /** Comentários e linhas de preprocessor fora da análise. */
    private fun limpar(src: String): String {
        val semBlocos = src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        val semLinhas = semBlocos.replace(Regex("//[^\n]*"), " ")
        return semLinhas.lines().filter { !it.trimStart().startsWith("#") }.joinToString("\n")
    }

    /** Declarações: identificador logo após um TIPO (uniform/in/out/const/local/parâmetro/função). */
    private fun declarados(limpo: String): Set<String> {
        val tiposAlt = tipos.joinToString("|")
        val re = Regex("\\b($tiposAlt)\\s+([A-Za-z_]\\w*)")
        return re.findAll(limpo).map { it.groupValues[2] }.toSet()
    }

    /** Identificadores usados: palavra não precedida de ponto (swizzle) nem dígito (1e-5). */
    private fun usados(limpo: String): Set<String> =
        Regex("(?<![\\w.])([A-Za-z_]\\w*)")
            .findAll(limpo)
            .map { it.groupValues[1] }
            .toSet()

    private fun naoDeclarados(src: String): Set<String> {
        val limpo = limpar(src)
        return usados(limpo) - declarados(limpo) - tipos - keywords - builtins
    }

    /** Todos os campos String *Shader do GlesBackend (fontes GLSL). */
    private fun fontesShaders(): List<Pair<String, String>> {
        val b = GlesBackend()
        return GlesBackend::class.java.declaredFields
            .filter { it.type == String::class.java && it.name.endsWith("Shader") }
            .map { f: Field ->
                assertTrue("campo ${f.name} deve ser val (final)", Modifier.isFinal(f.modifiers))
                f.isAccessible = true
                f.name to (f.get(b) as String)
            }
    }

    @Test
    fun `todo identificador usado nos shaders esta declarado`() {
        val problemas = ArrayList<String>(4)
        for ((nome, src) in fontesShaders()) {
            val faltando = naoDeclarados(src)
            if (faltando.isNotEmpty()) {
                problemas.add("$nome usa sem declarar: ${faltando.sorted().joinToString()}")
            }
        }
        assertTrue(
            "GLSL com identificador não declarado (o driver RECUSA compilar e o " +
                "backend cai no fallback de cor sólida — nenhuma luz): $problemas",
            problemas.isEmpty()
        )
    }

    @Test
    fun `fragment shader da malha declara a tangente que usa`() {
        val fs = fontesShaders().first { it.first == "fragmentShader" }.second
        assertTrue(
            "o fragmentShader da malha usa vTangent (normal map T0.4) e deve declarar " +
                "`in vec3 vTangent;` — sem isso o shader não compila e o viewport perde TODA a luz",
            Regex("in\\s+vec3\\s+vTangent\\s*;").containsMatchIn(fs)
        )
    }
}
