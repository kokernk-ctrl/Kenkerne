package com.kenkern.engine.core.script

import com.kenkern.engine.core.input.GameplayInput
import com.kenkern.engine.core.input.StickSample
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction

/** Install after carregar(), before iniciar(). Three numeric returns, not a Lua table per read. */
internal fun LuaJScriptRuntime.installGameplayInput(gameplay: GameplayInput) {
    val table = LuaTable()
    val sample = StickSample()
    fun bind(name: String, read: (Varargs) -> Varargs) {
        table.set(name, object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs = read(args)
        })
    }
    fun number(it: Float) = LuaValue.valueOf(it.toDouble())
    fun axes(includePressed: Boolean): Varargs {
        val tail = if (includePressed) LuaValue.varargsOf(number(sample.strength), LuaValue.valueOf(sample.pressed)) else number(sample.strength)
        return LuaValue.varargsOf(number(sample.x), number(sample.y), tail)
    }
    bind("joystick") { args ->
        if (gameplay.readJoystick(args.checkjstring(1), sample)) axes(false)
        else LuaValue.varargsOf(LuaValue.ZERO, LuaValue.ZERO, LuaValue.ZERO)
    }
    bind("action") { args -> gameplay.readAction(args.checkjstring(1), sample); axes(true) }
    // Monotonic sequence counters are not consumed by a reader. A press+release
    // between physics ticks remains observable without allocating event objects.
    bind("event_count") { args ->
        val byAction = args.optboolean(3, false)
        val name = args.checkjstring(1)
        if (byAction) gameplay.readAction(name, sample)
        if (!byAction && !gameplay.readJoystick(name, sample)) LuaValue.ZERO
        else LuaValue.valueOf(when (args.checkjstring(2)) {
            "on_press" -> sample.presses
            "on_move" -> sample.moves
            "on_release" -> sample.releases
            else -> 0
        })
    }
    instalarTabela("input", table)
}
