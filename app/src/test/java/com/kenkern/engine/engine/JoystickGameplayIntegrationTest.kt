package com.kenkern.engine.engine

import com.kenkern.engine.commands.CreateNickCommand
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.input.GameplayInput
import com.kenkern.engine.core.input.StickSample
import com.kenkern.engine.core.input.TouchControlSchema as Schema
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.script.LuaJScriptRuntime
import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.diag.ChecagemJoystick
import org.junit.Assert.*
import org.junit.Test

class JoystickGameplayIntegrationTest {
    @Test fun `synthetic diagnostic exercises production touch router and fixed step`() {
        assertTrue(ChecagemJoystick.executar().contains("x=.5"))
    }

    @Test fun `radial dead zone response curve normalization and octants`() {
        val s = StickSample()
        s.axes(.15f, .05f, .2f, 2f, false)
        near(0f, s.strength)
        s.axes(.6f, 0f, .2f, 2f, false)
        near(.25f, s.x)
        s.axes(4f, 2f, 0f, 1f, true)
        near(1f, s.strength); near(45f, s.angle); near(.7071068f, s.x); near(s.x, s.y)
    }

    @Test fun `add undo redo save load resolves target and drives only selected player with authored settings`() {
        NickClassRegistry.register(Schema.joystick)
        val editor = Scene("Authoring")
        val first = editor.createRootNick("First", kind = "body.player")
        val other = editor.createRootNick("Target", kind = "body.player")
        val create = CreateNickCommand(editor, null, Schema.JOYSTICK, "drive", listOf(Schema.STICK_CLASS))
        create.execute()
        val id = create.createdNickId()!!
        val fields = editor.findNick(id)!!.findClassInstance(Schema.STICK_CLASS)!!.fields
        fields["controls"] = NickValue.StringValue(other.id.value)
        fields["anchor"] = NickValue.StringValue("top_left")
        fields["space"] = NickValue.StringValue("camera")
        fields["sizeDp"] = NickValue.FloatValue(80f)
        fields["speed"] = NickValue.FloatValue(8f)
        fields["turn"] = NickValue.BoolValue(true)
        create.undo(); assertTrue(editor.linkerf.allPorts().isEmpty())
        create.execute(); assertEquals(8, editor.linkerf.allPorts().size)
        val play = CkFormat.parseToNewScene(CkFormat.writeToString(editor))!!
        assertEquals(editor.linkerf.allPorts().map { it.id }, play.linkerf.allPorts().map { it.id })
        val runtime = NickPlayRuntime(play)
        val input = runtime.input
        assertTrue(input.controls.any { it.fallback })
        assertTrue(input.down(7, 52f, 52f, 400f, 300f, 1f))
        assertTrue(input.move(7, 78f, 52f, 400f, 300f, 1f))
        runtime.stepTouch(.1f, 90f)
        val target = play.findNick(other.id)!!
        near(-.8f, target.transform.position.z)
        near(0f, play.findNick(first.id)!!.transform.position.z)
        near(0f, other.transform.position.z)
        input.up(7)
        assertTrue(input.down(8, 68f, 232f, 400f, 300f, 1f))
        assertTrue(input.move(8, 120f, 232f, 400f, 300f, 1f))
        runtime.stepTouch(.1f, 90f)
        near(.4f, play.findNick(first.id)!!.transform.position.x)
    }

    @Test fun `dynamic control follows contact while projection opens and closes a second target`() {
        val scene = Scene("Two thumbs")
        val target = scene.createRootNick("Target")
        fun stick(name: String, vararg fields: Pair<String, String>) {
            val n = scene.createRootNick(name, kind = Schema.JOYSTICK)
            n.addClassInstance(Schema.joystick.createInstance().also { c ->
                for ((key, value) in fields) c.fields[key] = NickValue.StringValue(value)
            })
        }
        stick("dynamic", "type" to "dynamic")
        stick("tracked", "anchor" to "follow", "follow" to target.id.value)
        target.transform.position.set(.5f, .5f, 0f)
        val runtime = NickPlayRuntime(scene)
        val input = runtime.input
        val identity = Mat4().setIdentity().m
        input.project(identity)
        val p = input.controls[1].projection
        near(.75f, GameplayInput.unpackX(p)); near(.25f, GameplayInput.unpackY(p))
        assertFalse(input.move(9, 300f, 75f, 400f, 300f, 1f))
        assertTrue(input.down(1, 100f, 230f, 400f, 300f, 1f))
        assertTrue(input.down(2, 300f, 75f, 400f, 300f, 1f))
        assertFalse(input.down(1, 300f, 75f, 400f, 300f, 1f))
        assertTrue(input.move(1, 300f, 230f, 400f, 300f, 1f))
        near(300f - 30.24f, input.controls[0].baseX)
        target.transform.position.x = 2f
        scene.updateWorldTransforms(); input.project(identity)
        assertEquals(Long.MIN_VALUE, input.controls[1].projection)
        val s = StickSample()
        assertTrue(input.readJoystick("tracked", s)); assertFalse(s.pressed); assertEquals(1, s.releases)
        assertTrue(input.readJoystick("dynamic", s)); assertTrue(s.pressed); near(1f, s.x)
        input.cancel(); input.readJoystick("dynamic", s); assertFalse(s.pressed); near(0f, s.x)
    }

    @Test fun `Lua gets named action and joystick multireturns from the same session`() {
        val scene = Scene("Lua integration")
        scene.createRootNick("stick", kind = Schema.JOYSTICK)
        scene.createRootNick("jump", kind = Schema.BUTTON)
        val runtime = NickPlayRuntime(scene)
        val lua = LuaJScriptRuntime("joystick", 1000)
        assertNull(lua.carregar("joystick", """
            function on_update(dt)
              x, y, strength = input.joystick('stick')
              _, _, _, jump = input.action('pular')
              presses = input.event_count('stick', 'on_press')
              releases = input.event_count('stick', 'on_release')
            end
        """.trimIndent()))
        runtime.bindInput(lua)
        assertTrue(lua.iniciar())
        val input = runtime.input
        assertTrue(input.down(1, 68f, 232f, 400f, 300f, 1f))
        assertTrue(input.move(1, 68f, 195f, 400f, 300f, 1f))
        assertTrue(input.down(2, 356f, 256f, 400f, 300f, 1f))
        assertTrue(lua.atualizar(.1f))
        val g = lua.ambiente()!!
        near(0f, g.get("x").tofloat()); near(-1f, g.get("y").tofloat()); near(1f, g.get("strength").tofloat())
        assertTrue(g.get("jump").toboolean()); assertEquals(1, g.get("presses").toint())
        input.up(1)
        assertTrue(lua.atualizar(.1f)); near(0f, g.get("y").tofloat()); assertEquals(1, g.get("releases").toint())
    }

    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, .001f)
}
