package com.kenkern.engine.render

import android.opengl.GLES30
import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.math.Mat4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Implementação OpenGL ES 3.0 do RenderBackend (minSdk 26).
 * Um único programa de shader com VAOs; os uniforms de luz vêm da LightingPipeline.
 */
class GlesBackend : RenderBackend {

    override val name: String = "OpenGL ES 3.0"

    override var viewportWidth: Int = 1
    override var viewportHeight: Int = 1

    private var program = 0
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uLightDir = 0
    private var uAmbient = 0
    private var uLightColor = 0
    private var uLightIntensity = 0

    private class GpuMesh(
        val vao: Int,
        val vbo: Int,
        val ibo: Int,
        val indexCount: Int,
        val indexType: Int
    )

    private class GpuLines(val vao: Int, val vbo: Int)

    private val meshes = HashMap<Int, GpuMesh>()
    private val lineBuffers = HashMap<Int, GpuLines>()
    private var nextHandle = 1

    private val mvpScratch = Mat4()
    private val uniformScratch = FloatArray(4)

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

    private val fragmentShader = """
        #version 300 es
        precision mediump float;
        in vec3 vNormal;
        uniform vec4 uColor;
        uniform vec3 uLightDir;
        uniform vec3 uAmbient;
        uniform vec3 uLightColor;
        uniform float uLightIntensity;
        out vec4 fragColor;
        void main() {
            vec3 n = normalize(vNormal);
            float diff = max(dot(n, normalize(-uLightDir)), 0.0);
            vec3 rgb = uColor.rgb * (uAmbient + uLightColor * diff * uLightIntensity);
            fragColor = vec4(rgb, uColor.a);
        }
    """.trimIndent()

    override fun initialize() {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexShader)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentShader)
        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            throw IllegalStateException("Falha ao linkar shader KENKERN: $log")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)

        uMvp = GLES30.glGetUniformLocation(program, "uMVP")
        uModel = GLES30.glGetUniformLocation(program, "uModel")
        uColor = GLES30.glGetUniformLocation(program, "uColor")
        uLightDir = GLES30.glGetUniformLocation(program, "uLightDir")
        uAmbient = GLES30.glGetUniformLocation(program, "uAmbient")
        uLightColor = GLES30.glGetUniformLocation(program, "uLightColor")
        uLightIntensity = GLES30.glGetUniformLocation(program, "uLightIntensity")

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glFrontFace(GLES30.GL_CCW)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("Falha ao compilar shader KENKERN: $log")
        }
        return shader
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
        GLES30.glUseProgram(program)
        uniformScratch[0] = dirX; uniformScratch[1] = dirY; uniformScratch[2] = dirZ
        GLES30.glUniform3fv(uLightDir, 1, uniformScratch, 0)
        uniformScratch[0] = ambientR; uniformScratch[1] = ambientG; uniformScratch[2] = ambientB
        GLES30.glUniform3fv(uAmbient, 1, uniformScratch, 0)
        uniformScratch[0] = lightR; uniformScratch[1] = lightG; uniformScratch[2] = lightB
        GLES30.glUniform3fv(uLightColor, 1, uniformScratch, 0)
        GLES30.glUniform1f(uLightIntensity, intensity)
        GLES30.glUseProgram(0)
    }

    override fun createMesh(mesh: MeshData): Int {
        val vertexCount = mesh.vertexCount
        val interleaved = ByteBuffer.allocateDirect(vertexCount * STRIDE).order(ByteOrder.nativeOrder())
        val fb = interleaved.asFloatBuffer()
        for (v in 0 until vertexCount) {
            fb.put(mesh.positions[v * 3])
            fb.put(mesh.positions[v * 3 + 1])
            fb.put(mesh.positions[v * 3 + 2])
            fb.put(mesh.normals[v * 3])
            fb.put(mesh.normals[v * 3 + 1])
            fb.put(mesh.normals[v * 3 + 2])
        }
        fb.position(0)

        val indexType: Int
        if (vertexCount <= 65535) {
            indexType = GLES30.GL_UNSIGNED_SHORT
            val vao = IntArray(1)
            GLES30.glGenVertexArrays(1, vao, 0)
            val vbo = IntArray(1)
            GLES30.glGenBuffers(1, vbo, 0)
            val ebo = IntArray(1)
            GLES30.glGenBuffers(1, ebo, 0)

            GLES30.glBindVertexArray(vao[0])
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexCount * STRIDE, fb, GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, STRIDE, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, STRIDE, 12)

            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo[0])
            val directShort = ByteBuffer.allocateDirect(mesh.indices.size * 2).order(ByteOrder.nativeOrder())
            val sb = directShort.asShortBuffer()
            for (i in mesh.indices) sb.put(i.toShort())
            sb.position(0)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 2, sb, GLES30.GL_STATIC_DRAW)

            GLES30.glBindVertexArray(0)
            val handle = nextHandle++
            meshes[handle] = GpuMesh(vao[0], vbo[0], ebo[0], mesh.indices.size, indexType)
            return handle
        }

        indexType = GLES30.GL_UNSIGNED_INT
        val vao = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        val vbo = IntArray(1)
        GLES30.glGenBuffers(1, vbo, 0)
        val ebo = IntArray(1)
        GLES30.glGenBuffers(1, ebo, 0)

        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexCount * STRIDE, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, STRIDE, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, STRIDE, 12)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo[0])
        val directInt = ByteBuffer.allocateDirect(mesh.indices.size * 4).order(ByteOrder.nativeOrder())
        val ibBuf = directInt.asIntBuffer()
        for (i in mesh.indices) ibBuf.put(i)
        ibBuf.position(0)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 4, ibBuf, GLES30.GL_STATIC_DRAW)

        GLES30.glBindVertexArray(0)
        val handle = nextHandle++
        meshes[handle] = GpuMesh(vao[0], vbo[0], ebo[0], mesh.indices.size, indexType)
        return handle
    }

    override fun createLineBuffer(positions: FloatArray): Int {
        val vao = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        val vbo = IntArray(1)
        GLES30.glGenBuffers(1, vbo, 0)
        val fb: FloatBuffer = ByteBuffer.allocateDirect(positions.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        fb.put(positions)
        fb.position(0)

        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, positions.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glVertexAttrib3f(1, 0f, 1f, 0f)
        GLES30.glBindVertexArray(0)

        val handle = nextHandle++
        lineBuffers[handle] = GpuLines(vao[0], vbo[0])
        return handle
    }

    override fun drawMesh(handle: Int, model: Mat4, viewProj: Mat4, r: Float, g: Float, b: Float, a: Float) {
        val gpu = meshes[handle] ?: return
        mvpScratch.multiply(viewProj, model)
        GLES30.glUseProgram(program)
        GLES30.glBindVertexArray(gpu.vao)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpScratch.m, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, model.m, 0)
        uniformScratch[0] = r; uniformScratch[1] = g; uniformScratch[2] = b; uniformScratch[3] = a
        GLES30.glUniform4fv(uColor, 1, uniformScratch, 0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, gpu.indexCount, gpu.indexType, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glUseProgram(0)
    }

    override fun drawLines(handle: Int, vertexCount: Int, mvp: Mat4, r: Float, g: Float, b: Float, a: Float) {
        val gpu = lineBuffers[handle] ?: return
        GLES30.glUseProgram(program)
        GLES30.glBindVertexArray(gpu.vao)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp.m, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, mvp.m, 0)
        uniformScratch[0] = r; uniformScratch[1] = g; uniformScratch[2] = b; uniformScratch[3] = a
        GLES30.glUniform4fv(uColor, 1, uniformScratch, 0)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertexCount)
        GLES30.glBindVertexArray(0)
        GLES30.glUseProgram(0)
    }

    override fun destroyMesh(handle: Int) {
        val gpu = meshes.remove(handle) ?: return
        val vao = IntArray(1); vao[0] = gpu.vao
        val bufs = IntArray(2); bufs[0] = gpu.vbo; bufs[1] = gpu.ibo
        GLES30.glDeleteVertexArrays(1, vao, 0)
        GLES30.glDeleteBuffers(2, bufs, 0)
    }

    override fun destroyLineBuffer(handle: Int) {
        val gpu = lineBuffers.remove(handle) ?: return
        val vao = IntArray(1); vao[0] = gpu.vao
        val bufs = IntArray(1); bufs[0] = gpu.vbo
        GLES30.glDeleteVertexArrays(1, vao, 0)
        GLES30.glDeleteBuffers(1, bufs, 0)
    }

    override fun endFrame() {
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
        for (line in lineBuffers.values) {
            val vao = IntArray(1); vao[0] = line.vao
            val bufs = IntArray(1); bufs[0] = line.vbo
            GLES30.glDeleteVertexArrays(1, vao, 0)
            GLES30.glDeleteBuffers(1, bufs, 0)
        }
        lineBuffers.clear()
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
    }

    companion object {
        private const val STRIDE = 24 // 3 pos + 3 normal (floats)
    }
}
