package com.kenkern.engine.engine

import com.kenkern.engine.commands.CommandHistory
import com.kenkern.engine.commands.CreateNickCommand
import com.kenkern.engine.commands.DeleteNickCommand
import com.kenkern.engine.commands.ReparentCommand
import com.kenkern.engine.commands.TransformCommand
import com.kenkern.engine.core.BobFunction
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickFunctionRegistry
import com.kenkern.engine.core.NickKindRegistry
import com.kenkern.engine.core.Scene
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSceneAccessTest {
    @Test
    fun frameRetainsHierarchyAndMatricesUntilTraversalFinishes() {
        val scene = Scene()
        val from = scene.createRootNick("From")
        val to = scene.createRootNick("To")
        from.transform.position.x = 10f
        to.transform.position.x = 20f
        val moving = scene.createNick("Moving", from)
        moving.transform.position.x = 1f
        val removing = scene.createNick("Removing", from)
        val history = CommandHistory()
        val add = CreateNickCommand(scene, to.id, NickKindRegistry.NICK_EMPTY)
        val frameEntered = CountDownLatch(1)
        val finishFrame = CountDownLatch(1)
        val frame = FutureTask {
            scene.withEditorAccess {
                scene.updateWorldTransforms()
                frameEntered.countDown()
                assertTrue(finishFrame.await(5, TimeUnit.SECONDS))
                assertEquals(listOf(moving, removing), from.children)
                assertTrue(to.children.isEmpty())
                assertEquals(11f, moving.transform.worldMatrix.m[12], 0f)
                assertEquals(1f, moving.transform.position.x, 0f)
            }
        }
        start(frame)

        val edits = FutureTask {
            scene.withEditorAccess {
                history.push(add)
                history.push(ReparentCommand(scene, moving.id, to.id))
                history.push(DeleteNickCommand(scene, removing.id))
                history.push(TransformCommand(scene, moving.id, moving.transform.snapshot().copy(px = 3f)))
            }
        }
        try {
            assertTrue(frameEntered.await(5, TimeUnit.SECONDS))
            awaitBlocked(start(edits))
        } finally {
            finishFrame.countDown()
        }
        frame.get(5, TimeUnit.SECONDS)
        edits.get(5, TimeUnit.SECONDS)

        scene.withEditorAccess {
            scene.updateWorldTransforms()
            assertNull(scene.findNick(removing.id))
            assertEquals(listOf(scene.findNick(add.createdNickId()!!)!!, moving), to.children)
            assertEquals(23f, moving.transform.worldMatrix.m[12], 0f)
            repeat(4) { history.undo() }
            scene.updateWorldTransforms()
            assertEquals(listOf(moving, removing), from.children)
            assertTrue(to.children.isEmpty())
            assertEquals(11f, moving.transform.worldMatrix.m[12], 0f)
        }
    }

    @Test
    fun playCopyWaitsForCompleteEditAndFixedUpdateDoesNotAffectEditor() {
        val scene = Scene("Editor")
        val nick = scene.createRootNick("Moving")
        nick.addClassInstance(NickClassInstance("Bob"))
        NickFunctionRegistry.register(BobFunction)

        val editEntered = CountDownLatch(1)
        val finishEdit = CountDownLatch(1)
        val edit = FutureTask {
            scene.withEditorAccess {
                nick.transform.position.x = 4f
                editEntered.countDown()
                assertTrue(finishEdit.await(5, TimeUnit.SECONDS))
                nick.transform.position.y = 2f
            }
        }
        start(edit)

        val capture = FutureTask { checkNotNull(scene.copyForPlay()) }
        try {
            assertTrue(editEntered.await(5, TimeUnit.SECONDS))
            awaitBlocked(start(capture))
        } finally {
            finishEdit.countDown()
        }
        edit.get(5, TimeUnit.SECONDS)
        val play = capture.get(5, TimeUnit.SECONDS)
        val copy = checkNotNull(play.findNick(nick.id))
        assertEquals("Editor", play.name)
        assertNotSame(nick, copy)
        assertEquals(nick.transform.snapshot(), copy.transform.snapshot())

        val loop = FixedStepLoop()
        loop.requestPlayState(PlayState.PLAYING)
        loop.tick(1L) { error("primeiro frame apenas registra o tempo") }
        loop.tick(100_000_001L) { dt -> play.updateFunctions(dt, loop.engineTime) }
        play.updateWorldTransforms()
        scene.withEditorAccess {
            assertSame(nick, scene.findNick(nick.id))
            assertEquals(2f, nick.transform.position.y, 0f)
            assertNotEquals(2f, copy.transform.worldMatrix.m[13], 0f)
        }
    }

    private fun start(task: FutureTask<*>): Thread = Thread(task).apply {
        isDaemon = true
        start()
    }

    /** Espera a entrada no monitor (sem depender de dar tempo ao worker com sleep). */
    private fun awaitBlocked(worker: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        while (worker.isAlive && worker.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.yield()
        }
        assertEquals(Thread.State.BLOCKED, worker.state)
    }
}
