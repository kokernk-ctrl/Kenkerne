package com.kenkern.engine.render

import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.assets.mesh.MeshData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.atomic.AtomicInteger

class EngineRendererContextResetTest {

    @Test
    fun `collider selecionado com a mesma chave recria buffer apos reset de contexto`() {
        val backend = Mockito.mock(RenderBackend::class.java)
        val handles = AtomicInteger(0)
        Mockito.`when`(backend.initialize(true)).thenReturn(true)
        Mockito.`when`(backend.viewportWidth).thenReturn(800)
        Mockito.`when`(backend.viewportHeight).thenReturn(600)
        Mockito.`when`(backend.createMesh(Mockito.any(MeshData::class.java))).thenReturn(1)
        Mockito.`when`(backend.createColoredLineBuffer(Mockito.any(FloatArray::class.java)))
            .thenAnswer { handles.incrementAndGet() }
        Mockito.`when`(backend.createGizmoBuffer(Mockito.any(FloatArray::class.java))).thenReturn(1000)

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
        val lineBuffersAfterFirstFrame = handles.get()

        // Em produção backend.destroy() precede initialize() após perda EGL.
        renderer.initialize(backend)
        renderer.renderFrame(backend, scene, camera, 0f, nick.id, gizmoState)

        // A segunda geração cria 13 buffers estáticos + o wireframe do collider.
        assertEquals(lineBuffersAfterFirstFrame + 14, handles.get())
    }
}
