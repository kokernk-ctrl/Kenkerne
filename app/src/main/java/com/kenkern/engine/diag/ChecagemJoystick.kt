package com.kenkern.engine.diag

import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.input.StickSample
import com.kenkern.engine.core.input.TouchControlSchema
import com.kenkern.engine.engine.NickPlayRuntime
import kotlin.math.abs

/** Injects synthetic contact coordinates through the same router as the Android HUD. */
internal object ChecagemJoystick {
    fun executar(): String {
        val scene = Scene("Joystick diagnostic")
        val player = scene.createRootNick("Player", kind = "body.player")
        val pad = scene.createRootNick("test-stick", kind = TouchControlSchema.JOYSTICK)
        pad.addClassInstance(TouchControlSchema.joystick.createInstance().also {
            it.fields["deadZone"] = NickValue.FloatValue(0f)
        })
        val button = scene.createRootNick("test-button", kind = TouchControlSchema.BUTTON)
        button.addClassInstance(TouchControlSchema.button.createInstance())
        val runtime = NickPlayRuntime(scene)
        val input = runtime.input
        val w = 400f; val h = 300f
        check(input.down(1, 68f, 232f, w, h, 1f)) { "joystick não capturou o primeiro dedo" }
        check(input.down(2, 356f, 256f, w, h, 1f)) { "botão não capturou o segundo dedo" }
        check(input.move(1, 83.12f, 232f, w, h, 1f)) { "joystick perdeu o primeiro dedo" }
        val sample = StickSample()
        check(input.readJoystick("test-stick", sample) && abs(sample.x - .5f) < .001f && abs(sample.y) < .001f) {
            "eixos x/y = ${sample.x}/${sample.y}; esperado .5/0"
        }
        runtime.stepTouch(.1f, 0f)
        check(abs(player.transform.position.x - .2f) < .001f) { "deslocamento X = ${player.transform.position.x}; esperado .2 m" }
        check(input.up(1)) { "joystick não liberou o primeiro dedo" }
        input.readAction("pular", sample)
        check(sample.pressed) { "soltar o joystick cancelou o botão" }
        input.cancel()
        input.readAction("pular", sample)
        check(!sample.pressed && sample.releases == 1) { "cancelamento deixou ação pressionada" }
        return "Toques sintéticos no roteador: x=.5 / y=0; jogador +.2 m; dois dedos e cancelamento OK"
    }
}
