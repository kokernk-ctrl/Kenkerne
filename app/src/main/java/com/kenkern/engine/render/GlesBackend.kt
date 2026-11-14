package com.kenkern.engine.render

import android.opengl.GLES30
import android.util.Log
import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.math.Mat4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Implementação OpenGL ES 3.0 do RenderBackend (minSdk 26).
 * Programas: malha (Lambert + ambiente hemisférico), céu (gradiente + sol),
 * grade (fade por distância), flat (contorno) e linhas coloridas (gizmo).
 *
 * PERF: estado GL rastreado — glUseProgram só é chamado quando o programa muda;
 * VAOs permanecem vinculados (o próximo draw vincula o seu); nenhum glUniform
 * redundante; grade = 1 VBO estático + 1 draw call; céu = 1 triângulo de tela
 * cheia; gizmo = 1 VBO colorido + 1 draw call.
 *
 * ANTI-CRASH: [initialize] NUNCA lança exceção — se a compilação/linkagem de um
 * programa falhar, aquele recurso é desativado (feature flag) e o resto continua.
 * Todas as chamadas são tolerantes a handles inválidos e erros de GL.
 */
class GlesBackend : RenderBackend {

    override val name: String = "OpenGL ES 3.0"

    override var viewportWidth: Int = 1
    override var viewportHeight: Int = 1

    /** False se nem o shader de fallback compilou: backend só limpa a tela. */
    @Volatile
    var isDegraded: Boolean = false
        private set

    // ---------- Programas ----------
    private var program = 0 // malha iluminada (Lambert + hemisférica)
    private var skyProgram = 0
    private var gridProgram = 0
    private var flatProgram = 0
    private var lineProgram = 0
    private var gizmoProgram = 0

    // Uniforms da malha
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uLightDir = 0
    private var uLightColor = 0
    private var uLightIntensity = 0

    // Uniforms do céu
    private var uSkyFwd = 0
    private var uSkyRight = 0
    private var uSkyUp = 0
    private var uSkyTanFov = 0
    private var uSkyAspect = 0
    private var uSkySun = 0

    // Uniforms da grade
    private var uGridVp = 0
    private var uGridCam = 0

    // Uniforms do flat (contorno)
    private var uFlatMvp = 0
    private var uFlatColor = 0

    // Uniforms do gizmo
    private var uLineMvp = 0

    // Uniforms do gizmo de transformação (cor RGBA por vértice)
    private var uTGizmoMvp = 0

    /** Programa atualmente vinculado (evita glUseProgram redundante). */
    private var boundProgram = -1

    // Feature flags (degradação por recurso)
    private var skyOk = false
    private var gridOk = false
    private var flatOk = false
    private var lineOk = false
    private var tGizmoOk = false

    // Sol (direção PARA o sol), derivado de setLighting
    private var sunDirX = 0.4f; private var sunDirY = 0.8f; private var sunDirZ = 0.3f

    private class GpuMesh(
        val vao: Int,
        val vbo: Int,
        val ibo: Int,
        val indexCount: Int,
        val indexType: Int
    )

    private class GpuLines(val vao: Int, val vbo: Int, val vertexCount: Int)

    private class GpuGizmo(val vao: Int, val vbo: Int, val vertexCount: Int)

    private val meshes = HashMap<Int, GpuMesh>()
    private val gridBuffers = HashMap<Int, GpuLines>()
    private val coloredLineBuffers = HashMap<Int, GpuLines>()
    private val tGizmoBuffers = HashMap<Int, GpuGizmo>()
    private var nextHandle = 1

    // VAO/VBO do triângulo de tela cheia (céu)
    private var skyVao = 0
    private var skyVbo = 0

    private val mvpScratch = Mat4()
    private val uniformScratch = FloatArray(4)
    private val overrideRgbaScratch = FloatArray(4)

    private val vertexShader = """
        #version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        uniform mat4 uMVP;
        uniform mat4 uModel;
        out vec3 vNormal;
        void main() {
            vNormal = mat3(uModel) * aNormal;
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """.trimIndent()

    /** Lambert + ambiente hemisférico (céu em cima, chão embaixo); sem topo estourado. */
    private val fragmentShader = """
        #version 300 es
        precision mediump float;
        in vec3 vNormal;
        uniform vec4 uColor;
        uniform vec3 uLightDir;
        uniform vec3 uLightColor;
        uniform float uLightIntensity;
        out vec4 fragColor;
        void main() {
            vec3 n = normalize(vNormal);
            float diff = max(dot(n, normalize(-uLightDir)), 0.0);
            vec3 hemi = mix(vec3(0.30, 0.28, 0.25), vec3(0.46, 0.54, 0.66), n.y * 0.5 + 0.5);
            vec3 rgb = uColor.rgb * (hemi + uLightColor * diff * uLightIntensity);
            fragColor = vec4(min(rgb, vec3(1.0)), uColor.a);
        }
    """.trimIndent()

    /** Fallback mínimo: cor sólida, sem iluminação — roda em praticamente qualquer GPU. */
    private val fallbackVertexShader = """
        #version 300 es
        layout(location = 0) in vec3 aPos;
        uniform mat4 uMVP;
        uniform mat4 uModel;
        void main() {
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """.trimIndent()

    private val fallbackFragmentShader = """
        #version 300 es
        precision mediump float;
        uniform vec4 uColor;
        out vec4 fragColor;
        void main() {
            fragColor = uColor;
        }
    """.trimIndent()

    // Céu: triângulo de tela cheia; raio por pixel reconstruído da base da câmera.
    private val skyVertexShader = """
        #version 300 es
        layout(location = 0) in vec2 aPos;
        out vec2 vNdc;
        void main() {
            vNdc = aPos;
            gl_Position = vec4(aPos, 0.9999, 1.0);
        }
    """.trimIndent()

    // Zênite #3A5A8C -> horizonte #B8C9DC; sol discreto na direção da luz.
    private val skyFragmentShader = """
        #version 300 es
        precision mediump float;
        in vec2 vNdc;
        uniform vec3 uFwd;
        uniform vec3 uRight;
        uniform vec3 uUp;
        uniform float uTanFov;
        uniform float uAspect;
        uniform vec3 uSun;
        out vec4 fragColor;
        void main() {
            vec3 dir = normalize(uFwd + uRight * (vNdc.x * uTanFov * uAspect) + uUp * (vNdc.y * uTanFov));
            float t = smoothstep(-0.06, 0.45, dir.y);
            vec3 sky = mix(vec3(0.722, 0.788, 0.863), vec3(0.227, 0.353, 0.549), t);
            sky *= 1.0 + min(dir.y, 0.0) * 0.30;
            float d = max(dot(dir, uSun), 0.0);
            float disc = smoothstep(0.99930, 0.99965, d);
            float glow = pow(d, 400.0) * 0.30;
            sky += vec3(1.0, 0.95, 0.80) * (disc * 0.85 + glow);
            fragColor = vec4(sky, 1.0);
        }
    """.trimIndent()

    // Grade: posição de mundo + ênfase por vértice (0=1m, 1=10m, 2=eixo X, 3=eixo Z).
    private val gridVertexShader = """
        #version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in float aEmphasis;
        uniform mat4 uVP;
        out vec3 vWorldPos;
        out float vEmphasis;
        void main() {
            vWorldPos = aPos;
            vEmphasis = aEmphasis;
            gl_Position = uVP * vec4(aPos, 1.0);
        }
    """.trimIndent()

    // Cor neutra, eixos vermelho/azul, fade por distância (alpha).
    private val gridFragmentShader = """
        #version 300 es
        precision mediump float;
        in vec3 vWorldPos;
        in float vEmphasis;
        uniform vec3 uCamPos;
        out vec4 fragColor;
        void main() {
            float dist = distance(vWorldPos.xz, uCamPos.xz);
            float fade = 1.0 - smoothstep(22.0, 85.0, dist);
            vec3 base;
            float alpha;
            if (vEmphasis > 2.5)      { base = vec3(0.36, 0.50, 0.88); alpha = 1.0;  }
            else if (vEmphasis > 1.5) { base = vec3(0.86, 0.32, 0.30); alpha = 1.0;  }
            else if (vEmphasis > 0.5) { base = vec3(0.58, 0.60, 0.64); alpha = 0.9;  }
            else                      { base = vec3(0.46, 0.48, 0.52); alpha = 0.55; }
            fragColor = vec4(base, alpha * fade);
        }
    """.trimIndent()

    // Flat (contorno inverted hull): cor sólida, sem iluminação.
    private val flatVertexShader = """
        #version 300 es
        layout(location = 0) in vec3 aPos;
        uniform mat4 uMVP;
        void main() {
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """.trimIndent()

    private val flatFragmentShader = """
        #version 300 es
        precision mediump float;
        uniform vec4 uColor;
        out vec4 fragColor;
        void main() {
            fragColor = uColor;
        }
    """.trimIndent()

    // Linhas coloridas (gizmo): cor por vértice.
    private val lineVertexShader = """
        #version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aColor;
        uniform mat4 uMVP;
        out vec3 vColor;
        void main() {
            vColor = aColor;
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """.trimIndent()

    private val lineFragmentShader = """
        #version 300 es
        precision mediump float;
        in vec3 vColor;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(vColor, 1.0);
        }
    """.trimIndent()

    // Gizmo de transformação: posição + cor RGBA por vértice (alpha nos planos).
    private val tGizmoVertexShader = """
        #version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec4 aColor;
        uniform mat4 uMVP;
        out vec4 vColor;
        void main() {
            vColor = aColor;
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """.trimIndent()

    private val tGizmoFragmentShader = """
        #version 300 es
        precision mediump float;
        in vec4 vColor;
        out vec4 fragColor;
        void main() {
            fragColor = vColor;
        }
    """.trimIndent()

    override fun initialize(): Boolean {
        GLES30.glDisable(GLES30.GL_DITHER)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glFrontFace(GLES30.GL_CCW)

        // 1) Programa completo; 2) fallback mínimo; 3) modo degradado.
        program = buildProgram(vertexShader, fragmentShader)
        if (program == 0) {
            Log.w(TAG, "Shader principal falhou; tentando fallback mínimo")
            program = buildProgram(fallbackVertexShader, fallbackFragmentShader)
        }
        if (program == 0) {
            Log.e(TAG, "Nenhum shader compilou: backend em modo degradado (somente clear)")
            isDegraded = true
            return true // app continua vivo; viewport mostra só o fundo
        }

        uMvp = GLES30.glGetUniformLocation(program, "uMVP")
        uModel = GLES30.glGetUniformLocation(program, "uModel")
        uColor = GLES30.glGetUniformLocation(program, "uColor")
        uLightDir = GLES30.glGetUniformLocation(program, "uLightDir")
        uLightColor = GLES30.glGetUniformLocation(program, "uLightColor")
        uLightIntensity = GLES30.glGetUniformLocation(program, "uLightIntensity")

        // Programas visuais: cada um é independente; se falhar, o recurso é desativado.
        runCatching {
            skyProgram = buildProgram(skyVertexShader, skyFragmentShader)
            if (skyProgram != 0) {
                uSkyFwd = GLES30.glGetUniformLocation(skyProgram, "uFwd")
                uSkyRight = GLES30.glGetUniformLocation(skyProgram, "uRight")
                uSkyUp = GLES30.glGetUniformLocation(skyProgram, "uUp")
                uSkyTanFov = GLES30.glGetUniformLocation(skyProgram, "uTanFov")
                uSkyAspect = GLES30.glGetUniformLocation(skyProgram, "uAspect")
                uSkySun = GLES30.glGetUniformLocation(skyProgram, "uSun")
                buildSkyTriangle()
                skyOk = skyVao != 0
            }
        }.onFailure { Log.w(TAG, "sky: ${it.message}") }

        runCatching {
            gridProgram = buildProgram(gridVertexShader, gridFragmentShader)
            if (gridProgram != 0) {
                uGridVp = GLES30.glGetUniformLocation(gridProgram, "uVP")
                uGridCam = GLES30.glGetUniformLocation(gridProgram, "uCamPos")
                gridOk = true
            }
        }.onFailure { Log.w(TAG, "grid: ${it.message}") }

        runCatching {
            flatProgram = buildProgram(flatVertexShader, flatFragmentShader)
            if (flatProgram != 0) {
                uFlatMvp = GLES30.glGetUniformLocation(flatProgram, "uMVP")
                uFlatColor = GLES30.glGetUniformLocation(flatProgram, "uColor")
                flatOk = true
            }
        }.onFailure { Log.w(TAG, "flat: ${it.message}") }

        runCatching {
            lineProgram = buildProgram(lineVertexShader, lineFragmentShader)
            if (lineProgram != 0) {
                uLineMvp = GLES30.glGetUniformLocation(lineProgram, "uMVP")
                lineOk = true
            }
        }.onFailure { Log.w(TAG, "line: ${it.message}") }

        runCatching {
            gizmoProgram = buildProgram(tGizmoVertexShader, tGizmoFragmentShader)
            if (gizmoProgram != 0) {
                uTGizmoMvp = GLES30.glGetUniformLocation(gizmoProgram, "uMVP")
                tGizmoOk = true
            }
        }.onFailure { Log.w(TAG, "tGizmo: ${it.message}") }

        GLES30.glClearColor(0.075f, 0.075f, 0.105f, 1f)
        drainGlErrors()
        return true
    }

    /** Triângulo de tela cheia em NDC (sem VBO por instância; criado uma vez). */
    private fun buildSkyTriangle() {
        val vao = IntArray(1)
        val vbo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        if (vao[0] == 0 || vbo[0] == 0) {
            if (vao[0] != 0) GLES30.glDeleteVertexArrays(1, vao, 0)
            if (vbo[0] != 0) GLES30.glDeleteBuffers(1, vbo, 0)
            drainGlErrors()
            return
        }
        val fb: FloatBuffer = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
        fb.position(0)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 6 * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)
        skyVao = vao[0]
        skyVbo = vbo[0]
        drainGlErrors()
    }

    /** Compila + linka sem lançar; retorna 0 em qualquer falha. */
    private fun buildProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vsSrc) ?: return 0
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fsSrc) ?: run {
            GLES30.glDeleteShader(vs)
            return 0
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        val status = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.w(TAG, "Link falhou: ${GLES30.glGetProgramInfoLog(p)}")
            GLES30.glDeleteProgram(p)
            drainGlErrors()
            return 0
        }
        return p
    }

    /** Compila um shader sem lançar; retorna null em falha. */
    private fun compile(type: Int, source: String): Int? {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) return null
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.w(TAG, "Compile falhou: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            drainGlErrors()
            return null
        }
        return shader
    }

    private fun drainGlErrors() {
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR) { /* descarta erros pendentes */ }
    }

    /** Vincula um programa apenas se ele mudou (evita glUseProgram redundante). */
    private fun useProgram(p: Int) {
        if (p != 0 && boundProgram != p) {
            GLES30.glUseProgram(p)
            boundProgram = p
        }
    }

    override fun resize(width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun beginFrame(clearR: Float, clearG: Float, clearB: Float, clearA: Float) {
        GLES30.glClearColor(clearR, clearG, clearB, clearA)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    }

    override fun setLighting(
        dirX: Float, dirY: Float, dirZ: Float,
        ambientR: Float, ambientG: Float, ambientB: Float,
        lightR: Float, lightG: Float, lightB: Float,
        intensity: Float
    ) {
        if (program == 0) return
        // Sol = direção PARA a luz (oposta ao sentido de viagem), normalizada uma vez por frame.
        val len = kotlin.math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
        if (len > 1e-6f) {
            sunDirX = -dirX / len; sunDirY = -dirY / len; sunDirZ = -dirZ / len
        }
        useProgram(program)
        uniformScratch[0] = dirX; uniformScratch[1] = dirY; uniformScratch[2] = dirZ
        GLES30.glUniform3fv(uLightDir, 1, uniformScratch, 0)
        uniformScratch[0] = lightR; uniformScratch[1] = lightG; uniformScratch[2] = lightB
        GLES30.glUniform3fv(uLightColor, 1, uniformScratch, 0)
        GLES30.glUniform1f(uLightIntensity, intensity)
    }

    override fun createMesh(mesh: MeshData): Int {
        if (mesh.vertexCount <= 0 || mesh.indices.isEmpty()) return 0
        val vertexCount = mesh.vertexCount

        val interleaved = runCatching {
            ByteBuffer.allocateDirect(vertexCount * STRIDE).order(ByteOrder.nativeOrder())
        }.getOrNull() ?: return 0
        val fb = interleaved.asFloatBuffer()
        for (v in 0 until vertexCount) {
            fb.put(mesh.positions[v * 3]); fb.put(mesh.positions[v * 3 + 1]); fb.put(mesh.positions[v * 3 + 2])
            fb.put(mesh.normals[v * 3]); fb.put(mesh.normals[v * 3 + 1]); fb.put(mesh.normals[v * 3 + 2])
        }
        fb.position(0)

        val useShort = vertexCount <= 65535
        val bytesPerIndex = if (useShort) 2 else 4
        val indexBuffer = runCatching {
            ByteBuffer.allocateDirect(mesh.indices.size * bytesPerIndex).order(ByteOrder.nativeOrder())
        }.getOrNull() ?: return 0
        if (useShort) {
            val sb = indexBuffer.asShortBuffer()
            for (i in mesh.indices) sb.put(i.toShort())
            sb.position(0)
        } else {
            val ib = indexBuffer.asIntBuffer()
            for (i in mesh.indices) ib.put(i)
            ib.position(0)
        }

        val vao = IntArray(1)
        val vbo = IntArray(1)
        val ebo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glGenBuffers(1, ebo, 0)
        if (vao[0] == 0 || vbo[0] == 0 || ebo[0] == 0) {
            if (vao[0] != 0) GLES30.glDeleteVertexArrays(1, vao, 0)
            if (vbo[0] != 0) GLES30.glDeleteBuffers(1, vbo, 0)
            if (ebo[0] != 0) GLES30.glDeleteBuffers(1, ebo, 0)
            drainGlErrors()
            return 0
        }

        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexCount * STRIDE, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, STRIDE, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, STRIDE, 12)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo[0])
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * bytesPerIndex, indexBuffer, GLES30.GL_STATIC_DRAW
        )
        GLES30.glBindVertexArray(0)
        drainGlErrors()

        val handle = nextHandle++
        meshes[handle] = GpuMesh(
            vao[0], vbo[0], ebo[0], mesh.indices.size,
            if (useShort) GLES30.GL_UNSIGNED_SHORT else GLES30.GL_UNSIGNED_INT
        )
        return handle
    }

    override fun createGridBuffer(data: FloatArray): Int {
        if (data.isEmpty()) return 0
        val vertexCount = data.size / 4
        val vao = IntArray(1)
        val vbo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        if (vao[0] == 0 || vbo[0] == 0) {
            if (vao[0] != 0) GLES30.glDeleteVertexArrays(1, vao, 0)
            if (vbo[0] != 0) GLES30.glDeleteBuffers(1, vbo, 0)
            drainGlErrors()
            return 0
        }
        val fb: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(data)
        fb.position(0)
        // Interleaved: x, y, z (offset 0) + ênfase (offset 12) — stride 16.
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, 16, 12)
        GLES30.glBindVertexArray(0)
        drainGlErrors()

        val handle = nextHandle++
        gridBuffers[handle] = GpuLines(vao[0], vbo[0], vertexCount)
        return handle
    }

    override fun createColoredLineBuffer(data: FloatArray): Int {
        if (data.isEmpty()) return 0
        val vertexCount = data.size / 6
        val vao = IntArray(1)
        val vbo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        if (vao[0] == 0 || vbo[0] == 0) {
            if (vao[0] != 0) GLES30.glDeleteVertexArrays(1, vao, 0)
            if (vbo[0] != 0) GLES30.glDeleteBuffers(1, vbo, 0)
            drainGlErrors()
            return 0
        }
        val fb: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(data)
        fb.position(0)
        // Interleaved: x, y, z (offset 0) + r, g, b (offset 12) — stride 24.
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
        GLES30.glBindVertexArray(0)
        drainGlErrors()

        val handle = nextHandle++
        coloredLineBuffers[handle] = GpuLines(vao[0], vbo[0], vertexCount)
        return handle
    }

    /** Gizmo de transformação: interleaved x,y,z,r,g,b,a (stride 28). */
    override fun createGizmoBuffer(data: FloatArray): Int {
        if (data.isEmpty()) return 0
        val vertexCount = data.size / 7
        val vao = IntArray(1)
        val vbo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        if (vao[0] == 0 || vbo[0] == 0) {
            if (vao[0] != 0) GLES30.glDeleteVertexArrays(1, vao, 0)
            if (vbo[0] != 0) GLES30.glDeleteBuffers(1, vbo, 0)
            drainGlErrors()
            return 0
        }
        val fb: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(data)
        fb.position(0)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 28, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 28, 12)
        GLES30.glBindVertexArray(0)
        drainGlErrors()

        val handle = nextHandle++
        tGizmoBuffers[handle] = GpuGizmo(vao[0], vbo[0], vertexCount)
        return handle
    }

    override fun drawSky(
        fwdX: Float, fwdY: Float, fwdZ: Float,
        rightX: Float, rightY: Float, rightZ: Float,
        upX: Float, upY: Float, upZ: Float,
        tanHalfFov: Float, aspect: Float,
        sunX: Float, sunY: Float, sunZ: Float
    ) {
        if (skyProgram == 0 || !skyOk || skyVao == 0) return
        // Céu primeiro, sem teste nem escrita de depth — o resto desenha por cima.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        useProgram(skyProgram)
        GLES30.glBindVertexArray(skyVao)
        uniformScratch[0] = fwdX; uniformScratch[1] = fwdY; uniformScratch[2] = fwdZ
        GLES30.glUniform3fv(uSkyFwd, 1, uniformScratch, 0)
        uniformScratch[0] = rightX; uniformScratch[1] = rightY; uniformScratch[2] = rightZ
        GLES30.glUniform3fv(uSkyRight, 1, uniformScratch, 0)
        uniformScratch[0] = upX; uniformScratch[1] = upY; uniformScratch[2] = upZ
        GLES30.glUniform3fv(uSkyUp, 1, uniformScratch, 0)
        GLES30.glUniform1f(uSkyTanFov, tanHalfFov)
        GLES30.glUniform1f(uSkyAspect, aspect)
        uniformScratch[0] = sunX; uniformScratch[1] = sunY; uniformScratch[2] = sunZ
        GLES30.glUniform3fv(uSkySun, 1, uniformScratch, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    override fun drawGrid(handle: Int, vertexCount: Int, viewProj: Mat4, camX: Float, camY: Float, camZ: Float) {
        if (gridProgram == 0 || !gridOk || vertexCount <= 0) return
        val gpu = gridBuffers[handle] ?: return
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        useProgram(gridProgram)
        GLES30.glBindVertexArray(gpu.vao)
        GLES30.glUniformMatrix4fv(uGridVp, 1, false, viewProj.m, 0)
        uniformScratch[0] = camX; uniformScratch[1] = camY; uniformScratch[2] = camZ
        GLES30.glUniform3fv(uGridCam, 1, uniformScratch, 0)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertexCount)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    override fun drawMesh(handle: Int, model: Mat4, viewProj: Mat4, r: Float, g: Float, b: Float, a: Float) {
        if (program == 0) return
        val gpu = meshes[handle] ?: return
        mvpScratch.multiply(viewProj, model)
        useProgram(program)
        GLES30.glBindVertexArray(gpu.vao)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpScratch.m, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, model.m, 0)
        uniformScratch[0] = r; uniformScratch[1] = g; uniformScratch[2] = b; uniformScratch[3] = a
        GLES30.glUniform4fv(uColor, 1, uniformScratch, 0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, gpu.indexCount, gpu.indexType, 0)
    }

    override fun drawMeshOutline(handle: Int, model: Mat4, viewProj: Mat4, r: Float, g: Float, b: Float, a: Float) {
        if (!flatOk || flatProgram == 0) return
        val gpu = meshes[handle] ?: return
        mvpScratch.multiply(viewProj, model)
        useProgram(flatProgram)
        GLES30.glBindVertexArray(gpu.vao)
        GLES30.glCullFace(GLES30.GL_FRONT) // inverted hull: mostra as backfaces infladas
        GLES30.glUniformMatrix4fv(uFlatMvp, 1, false, mvpScratch.m, 0)
        uniformScratch[0] = r; uniformScratch[1] = g; uniformScratch[2] = b; uniformScratch[3] = a
        GLES30.glUniform4fv(uFlatColor, 1, uniformScratch, 0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, gpu.indexCount, gpu.indexType, 0)
        GLES30.glCullFace(GLES30.GL_BACK)
    }

    override fun drawGizmo(handle: Int, vertexCount: Int, mvp: Mat4) {
        if (!lineOk || lineProgram == 0 || vertexCount <= 0) return
        val gpu = coloredLineBuffers[handle] ?: return
        // Canto superior direito (origem GL é bottom-left): quadrado de ~1/4 do menor lado.
        val size = (minOf(viewportWidth, viewportHeight) / 4).coerceIn(80, 140)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glViewport(viewportWidth - size, 0, size, size)
        useProgram(lineProgram)
        GLES30.glBindVertexArray(gpu.vao)
        GLES30.glUniformMatrix4fv(uLineMvp, 1, false, mvp.m, 0)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertexCount)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    /** Desenha um sub-range do buffer do gizmo de transformação (sem depth, com blend). */
    private fun drawTGizmoRange(
        handle: Int, offset: Int, vertexCount: Int, mvp: Mat4,
        primitive: Int, overrideRgba: FloatArray?
    ) {
        if (!tGizmoOk || vertexCount <= 0) return
        val gpu = tGizmoBuffers[handle] ?: return
        val end = offset + vertexCount
        if (offset < 0 || end > gpu.vertexCount) return
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        if (overrideRgba != null) {
            // destaque de alça: programa flat (cor sólida uniforme)
            useProgram(flatProgram)
            GLES30.glBindVertexArray(gpu.vao)
            GLES30.glUniformMatrix4fv(uFlatMvp, 1, false, mvp.m, 0)
            uniformScratch[0] = overrideRgba[0]
            uniformScratch[1] = overrideRgba[1]
            uniformScratch[2] = overrideRgba[2]
            uniformScratch[3] = overrideRgba[3]
            GLES30.glUniform4fv(uFlatColor, 1, uniformScratch, 0)
        } else {
            useProgram(gizmoProgram)
            GLES30.glBindVertexArray(gpu.vao)
            GLES30.glUniformMatrix4fv(uTGizmoMvp, 1, false, mvp.m, 0)
        }
        GLES30.glDrawArrays(primitive, offset, vertexCount)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    override fun drawGizmoLines(handle: Int, offset: Int, vertexCount: Int, mvp: Mat4) {
        drawTGizmoRange(handle, offset, vertexCount, mvp, GLES30.GL_LINES, null)
    }

    override fun drawGizmoTris(handle: Int, offset: Int, vertexCount: Int, mvp: Mat4) {
        drawTGizmoRange(handle, offset, vertexCount, mvp, GLES30.GL_TRIANGLES, null)
    }

    override fun drawGizmoHandleOverride(
        handle: Int, offset: Int, vertexCount: Int, mvp: Mat4,
        r: Float, g: Float, b: Float, a: Float
    ) {
        overrideRgbaScratch[0] = r; overrideRgbaScratch[1] = g
        overrideRgbaScratch[2] = b; overrideRgbaScratch[3] = a
        drawTGizmoRange(handle, offset, vertexCount, mvp, GLES30.GL_LINES, overrideRgbaScratch)
    }

    override fun destroyMesh(handle: Int) {
        val gpu = meshes.remove(handle) ?: return
        val vao = IntArray(1); vao[0] = gpu.vao
        val bufs = IntArray(2); bufs[0] = gpu.vbo; bufs[1] = gpu.ibo
        GLES30.glDeleteVertexArrays(1, vao, 0)
        GLES30.glDeleteBuffers(2, bufs, 0)
    }

    override fun destroyLineBuffer(handle: Int) {
        val gpu = gridBuffers.remove(handle) ?: coloredLineBuffers.remove(handle) ?: return
        val vao = IntArray(1); vao[0] = gpu.vao
        val bufs = IntArray(1); bufs[0] = gpu.vbo
        GLES30.glDeleteVertexArrays(1, vao, 0)
        GLES30.glDeleteBuffers(1, bufs, 0)
    }

    override fun endFrame() {
        GLES30.glBindVertexArray(0)
        // Swap é feito pelo GLSurfaceView.
    }

    override fun destroy() {
        for (mesh in meshes.values) {
            val vao = IntArray(1); vao[0] = mesh.vao
            val bufs = IntArray(2); bufs[0] = mesh.vbo; bufs[1] = mesh.ibo
            GLES30.glDeleteVertexArrays(1, vao, 0)
            GLES30.glDeleteBuffers(2, bufs, 0)
        }
        meshes.clear()
        for (line in gridBuffers.values) {
            val vao = IntArray(1); vao[0] = line.vao
            val bufs = IntArray(1); bufs[0] = line.vbo
            GLES30.glDeleteVertexArrays(1, vao, 0)
            GLES30.glDeleteBuffers(1, bufs, 0)
        }
        gridBuffers.clear()
        for (line in coloredLineBuffers.values) {
            val vao = IntArray(1); vao[0] = line.vao
            val bufs = IntArray(1); bufs[0] = line.vbo
            GLES30.glDeleteVertexArrays(1, vao, 0)
            GLES30.glDeleteBuffers(1, bufs, 0)
        }
        coloredLineBuffers.clear()
        for (gizmo in tGizmoBuffers.values) {
            val vao = IntArray(1); vao[0] = gizmo.vao
            val bufs = IntArray(1); bufs[0] = gizmo.vbo
            GLES30.glDeleteVertexArrays(1, vao, 0)
            GLES30.glDeleteBuffers(1, bufs, 0)
        }
        tGizmoBuffers.clear()
        if (skyVao != 0) {
            val vao = IntArray(1); vao[0] = skyVao
            val vbo = IntArray(1); vbo[0] = skyVbo
            GLES30.glDeleteVertexArrays(1, vao, 0)
            GLES30.glDeleteBuffers(1, vbo, 0)
            skyVao = 0; skyVbo = 0
        }
        if (program != 0) { GLES30.glDeleteProgram(program); program = 0 }
        if (skyProgram != 0) { GLES30.glDeleteProgram(skyProgram); skyProgram = 0 }
        if (gridProgram != 0) { GLES30.glDeleteProgram(gridProgram); gridProgram = 0 }
        if (flatProgram != 0) { GLES30.glDeleteProgram(flatProgram); flatProgram = 0 }
        if (lineProgram != 0) { GLES30.glDeleteProgram(lineProgram); lineProgram = 0 }
        if (gizmoProgram != 0) { GLES30.glDeleteProgram(gizmoProgram); gizmoProgram = 0 }
        boundProgram = -1
        skyOk = false; gridOk = false; flatOk = false; lineOk = false; tGizmoOk = false
        drainGlErrors()
    }

    companion object {
        private const val TAG = "Kenkern/GlesBackend"
        private const val STRIDE = 24 // 3 pos + 3 normal (floats)
    }
}
