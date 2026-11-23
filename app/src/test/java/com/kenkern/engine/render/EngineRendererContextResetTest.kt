package com.kenkern.engine.render

import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineRendererContextResetTest {

    @Test
    fun `collider selecionado com a mesma chave recria buffer apos reset de contexto`() {
        val backend = RenderBackendTestDouble()

        val renderer = EngineRenderer()
        val scene = Scene("context-reset")
        val nick = scene.createRootNick("colisor", kind = "body.collider")
        nick.addClassInstance(
            NickClassInstance("Colisor").apply {
                fields["forma"] = NickValue.StringValue("box")
                fields["centro"] = NickValue.Vec3Value(0f, 0f, 0f)
                fields["tamanho"] = NickValue.Vec3Value(1f, 1f, 1f)
            }
        )
        scene.updateWorldTransforms()
        val camera = OrbitCamera()
        val gizmoState = GizmoSharedState()

        renderer.initialize(backend)
        renderer.renderFrame(backend, scene, camera, 0f, nick.id, gizmoState)
        val lineBuffersAfterFirstFrame = backend.coloredLineBufferCreates

        // Em produção backend.destroy() precede initialize() após perda EGL.
        renderer.initialize(backend)
        renderer.renderFrame(backend, scene, camera, 0f, nick.id, gizmoState)

        // A segunda geração cria 13 buffers estáticos + o wireframe do collider.
        assertEquals(lineBuffersAfterFirstFrame + 14, backend.coloredLineBufferCreates)
    }
}
