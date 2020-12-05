package com.github.omarmiatello.yeelight

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlin.time.*

private val json = Json {
    encodeDefaults = true
    serializersModule = SerializersModule {
        contextual(String.serializer())
        contextual(Int.serializer())
        contextual(Long.serializer())
    }
}

@OptIn(ExperimentalTime::class)
private fun colorFlowBuilder(
    flowTuples: List<FlowTuple>,
    repeat: Int = 1,
    action: FlowEndAction = FlowEndAction.recover
) = listOf(repeat * flowTuples.size, action.id, flowTuples.joinToString(","))

// Docs: http://www.yeelight.com/download/Yeelight_Inter-Operation_Spec.pdf
@OptIn(ExperimentalTime::class)
object YeelightApi {
    fun getProperties(vararg propertiesNames: String) = YeelightCmd("get_prop", propertiesNames.toList())

    fun setCurrentAsDefault() = YeelightCmd("set_default")

    fun setPower(
        isOn: Boolean = true,
        effect: SpeedEffect = SpeedEffect.smooth,
        duration: Duration = 500.milliseconds
    ) = YeelightCmd("set_power", listOf(if (isOn) "on" else "off", effect.id, duration.toLongMilliseconds()))

    fun toggle() = YeelightCmd("toggle")

    /**
     * brightness: 1 - 100
     */
    fun setBrightness(
        brightness: Int,
        effect: SpeedEffect = SpeedEffect.smooth,
        duration: Duration = 500.milliseconds
    ) = YeelightCmd("set_bright", listOf(brightness.coerceIn(1..100), effect.id, duration.toLongMilliseconds()))

    fun startColorFlowRaw(
        count: Int,
        action: FlowEndAction,
        flowExpression: String
    ) = YeelightCmd("start_cf", listOf(count, action.id, flowExpression))

    fun startColorFlow(
        flowTuples: List<FlowTuple>,
        repeat: Int = 1,
        action: FlowEndAction = FlowEndAction.recover
    ) = YeelightCmd("start_cf", colorFlowBuilder(flowTuples, repeat, action))

    fun stopColorFlow() = YeelightCmd("stop_cf")

    fun setScene(scene: YeelightScene) = YeelightCmd("set_scene", listOf(scene.name, scene.params))

    fun cronAdd(
        cron: YeelightCron
    ) = YeelightCmd("cron_add", listOf(cron.type, cron.duration.coerceAtLeast(1.minutes).inMinutes.toInt()))

    fun cronGet() = YeelightCmd("cron_get")
    fun cronDel() = YeelightCmd("cron_del")

    /**
     * colorTemperature: 1700 ~ 6500
     */
    fun setWhiteTemperature(
        whiteTemperature: Int,
        effect: SpeedEffect = SpeedEffect.smooth,
        duration: Duration = 500.milliseconds
    ) = YeelightCmd(
        "set_ct_abx",
        listOf(whiteTemperature.coerceIn(1700..6500), effect.id, duration.toLongMilliseconds())
    )

    fun setColorRgb(
        color: Int,
        effect: SpeedEffect = SpeedEffect.smooth,
        duration: Duration = 500.milliseconds
    ) = YeelightCmd("set_rgb", listOf(color.coerceIn(0..0xffffff), effect.id, duration.toLongMilliseconds()))

    /**
     * hue: 0 - 359
     * sat: 0 - 100
     * */
    fun setColorHsv(
        hue: Int,
        sat: Int,
        effect: SpeedEffect = SpeedEffect.smooth,
        duration: Duration = 500.milliseconds
    ) = YeelightCmd(
        "set_hsv",
        listOf(hue.coerceIn(0..359), sat.coerceIn(0..100), effect.id, duration.toLongMilliseconds())
    )
}


// All API

@Serializable
data class YeelightCmd(val method: String, val params: List<@Contextual Any> = emptyList()) {
    val id = 1

    @Transient
    val realCommand = json.encodeToString(this)
}

enum class SpeedEffect {
    sudden, smooth;

    val id = name
}

enum class FlowEndAction(val id: Int) { recover(0), stay(1), off(2) }
private enum class FlowMode(val id: Int) { colorRgb(1), colorTemperature(2), sleep(7) }


// Scene

sealed class YeelightScene(val name: String, vararg val params: Any)
class SceneColorHsv(
    hue: Int,
    sat: Int,
    brightness: Int
) : YeelightScene("hsv", hue, sat, brightness)

class SceneColorRgb(
    color: Int,
    brightness: Int
) : YeelightScene("ct", color, brightness)

class SceneColorFlow(
    flowTuples: List<FlowTuple>,
    repeat: Int = 1,
    action: FlowEndAction = FlowEndAction.recover
) : YeelightScene("cf", *colorFlowBuilder(flowTuples, repeat, action).toTypedArray())

@OptIn(ExperimentalTime::class)
class SceneAutoDelayOff(
    brightness: Int,
    duration: Duration = 1.minutes
) : YeelightScene("auto_delay_off", brightness, duration.coerceAtLeast(1.minutes).inMinutes.toInt())

// Cron
@OptIn(ExperimentalTime::class)
sealed class YeelightCron(val type: Int, val duration: Duration)

@OptIn(ExperimentalTime::class)
class CronPowerOff(
    duration: Duration
) : YeelightCron(0, duration)

// Color Flow

@OptIn(ExperimentalTime::class)
sealed class FlowTuple(
    val duration: Duration,
    private val mode: FlowMode,
    val value: Int,
    val brightness: Int
) {
    override fun toString() = "${duration.toLongMilliseconds()},${mode.id},$value,$brightness"
}

@OptIn(ExperimentalTime::class)
class FlowColor(
    color: Int,
    brightness: Int = 100,
    duration: Duration = 1.seconds,
) : FlowTuple(
    duration = duration.coerceAtLeast(50.milliseconds),
    mode = FlowMode.colorRgb,
    value = color.coerceIn(0x000000..0xFFFFFF),
    brightness = brightness.coerceIn(-1..100)
)

@OptIn(ExperimentalTime::class)
class FlowSleep(
    duration: Duration = 1.seconds
) : FlowTuple(
    duration = duration.coerceAtLeast(50.milliseconds),
    mode = FlowMode.sleep,
    value = 0,
    brightness = 0
)

@OptIn(ExperimentalTime::class)
class FlowWhiteTemperature(
    whiteTemperature: Int,
    brightness: Int = 100,
    duration: Duration = 1.seconds,
) : FlowTuple(
    duration = duration.coerceAtLeast(50.milliseconds),
    mode = FlowMode.colorTemperature,
    value = whiteTemperature.coerceIn(1700..6500),
    brightness = brightness.coerceIn(-1..100)
)

