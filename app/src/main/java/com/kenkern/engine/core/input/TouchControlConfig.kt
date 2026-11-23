package com.kenkern.engine.core.input

import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassDefinition
import com.kenkern.engine.core.NickFieldDefinition
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.linkerf.LinkerfPort
import com.kenkern.engine.core.linkerf.LinkerfPortKind
import com.kenkern.engine.core.linkerf.LinkerfPortType

/** The fields use CK2's existing typed class storage; targets are stable Nick IDs. */
object TouchControlSchema {
    const val JOYSTICK = "ui.joystick"
    const val BUTTON = "ui.action_button"
    const val STICK_CLASS = "Joystick"
    const val BUTTON_CLASS = "ActionButton"
    const val MOVE = "mover"
    const val LOOK = "olhar"

    private fun field(name: String, value: NickValue) = NickFieldDefinition(name, value.typeName, value)
    private fun text(name: String, default: String = "") = field(name, NickValue.StringValue(default))
    private fun number(name: String, default: Float) = field(name, NickValue.FloatValue(default))
    private fun flag(name: String) = field(name, NickValue.BoolValue(false))

    val joystick = NickClassDefinition(STICK_CLASS, "Touch joystick", listOf(
        text("type", "fixed"), number("sizeDp", 112f), number("opacity", .82f), number("deadZone", .12f),
        number("response", 1f), flag("snap8"), text("anchor", "bottom_left"), text("follow"),
        text("controls"), text("space", "world"), number("speed", 4f), flag("turn"), text("action", MOVE)
    ))
    val button = NickClassDefinition(BUTTON_CLASS, "Named touch action", listOf(
        number("sizeDp", 64f), number("opacity", .82f), text("anchor", "bottom_right"),
        text("follow"), text("action", "pular")
    ))

    /** Stable data/event port IDs survive rename and save/reopen. */
    fun ports(nick: Nick): List<LinkerfPort> {
        fun port(name: String, kind: LinkerfPortKind, type: LinkerfPortType) =
            LinkerfPort(ReferenceId("${nick.id.value}:input:$name"), nick.id, name, kind, type)
        fun data(name: String, type: LinkerfPortType) = port(name, LinkerfPortKind.DATA_OUT, type)
        fun event(name: String) = port(name, LinkerfPortKind.EVENT_OUT, LinkerfPortType.TRIGGER)
        if (nick.kind == BUTTON) return listOf(data("pressed", LinkerfPortType.BOOL), event("on_press"), event("on_release"))
        if (nick.kind != JOYSTICK) return emptyList()
        return listOf(data("x", LinkerfPortType.FLOAT), data("y", LinkerfPortType.FLOAT),
            data("strength", LinkerfPortType.FLOAT), data("angle", LinkerfPortType.FLOAT),
            data("pressed", LinkerfPortType.BOOL), event("on_press"), event("on_move"), event("on_release"))
    }
}

enum class StickType(val key: String) { FIXED("fixed"), FLOATING("floating"), DYNAMIC("dynamic") }
enum class TouchAnchor(val key: String) {
    BOTTOM_LEFT("bottom_left"), BOTTOM_RIGHT("bottom_right"), TOP_LEFT("top_left"), TOP_RIGHT("top_right"), FOLLOW("follow")
}

internal data class TouchControlConfig(
    val name: String,
    val action: String,
    val button: Boolean = false,
    val type: StickType = StickType.FIXED,
    val sizeDp: Float = 112f,
    val opacity: Float = .82f,
    val deadZone: Float = .12f,
    val response: Float = 1f,
    val snap8: Boolean = false,
    val anchor: TouchAnchor = TouchAnchor.BOTTOM_LEFT,
    val followId: String = "",
    val targetId: String = "",
    val cameraRelative: Boolean = false,
    val speed: Float = 4f,
    val turn: Boolean = false
) {
    companion object {
        fun fromNick(nick: Nick): TouchControlConfig {
            val button = nick.kind == TouchControlSchema.BUTTON
            val inst = nick.findClassInstance(if (button) TouchControlSchema.BUTTON_CLASS else TouchControlSchema.STICK_CLASS)
            fun text(key: String, default: String = "") = inst?.getString(key, default)?.trim() ?: default
            fun number(key: String, default: Float, low: Float, high: Float): Float =
                inst?.getFloat(key, default)?.takeIf { it.isFinite() }?.coerceIn(low, high) ?: default
            val anchor = if (button) TouchAnchor.BOTTOM_RIGHT else TouchAnchor.BOTTOM_LEFT
            val action = if (button) "pular" else TouchControlSchema.MOVE
            return TouchControlConfig(
                nick.name?.takeIf { it.isNotBlank() } ?: nick.id.value, text("action", action).ifBlank { action }, button,
                StickType.entries.firstOrNull { it.key == text("type") } ?: StickType.FIXED,
                number("sizeDp", if (button) 64f else 112f, 40f, 240f), number("opacity", .82f, 0f, 1f),
                number("deadZone", .12f, 0f, .95f), number("response", 1f, .25f, 4f), inst?.getBool("snap8", false) == true,
                TouchAnchor.entries.firstOrNull { it.key == text("anchor") } ?: anchor,
                text("follow"), text("controls"), text("space") == "camera", number("speed", 4f, 0f, 100f),
                inst?.getBool("turn", false) == true
            )
        }
    }
}
