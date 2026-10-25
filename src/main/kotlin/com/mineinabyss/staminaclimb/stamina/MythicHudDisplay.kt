package com.mineinabyss.staminaclimb.stamina

import com.google.common.base.Function
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.staminaclimb.modules.stamina
import io.lumine.mythichud.api.HudHolder
import io.lumine.mythichud.api.MythicHUD
import io.lumine.mythichud.api.hud.layer.listener.LayerListener
import io.lumine.mythichud.api.util.MathUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

object MythicHudDisplay : StaminaDisplay {
    private val listeners = arrayOf<LayerListener>(StaminaListener, StaminaAlertListener)

    fun register() {
        listeners.forEach { MythicHUD.getInstance().listeners().register(stamina.plugin, it) }
    }

    fun unregister() {
        MythicHUD.getInstance().listeners().unregister(stamina.plugin)
    }

    override fun show(player: Player, stamina: PlayerStamina) = push(player)

    override fun update(player: Player, stamina: PlayerStamina) = push(player)

    override fun hide(uuid: UUID) {
        uuid.toPlayer()?.let(::push)
    }

    override fun hideAll() {
        Bukkit.getOnlinePlayers().forEach(::push)
    }

    private fun push(player: Player) {
        HudHolder.get(player)?.update(*listeners)
    }

    private abstract class StaminaLayerListener(private val key: String) : LayerListener {
        private val placeholders = mapOf<String, Function<HudHolder, String>>(
            "value" to Function { MathUtil.toFormatted(getValue(it)) },
            "max" to Function { MathUtil.toFormatted(getMax(it)) },
            "percent" to Function { MathUtil.toFormatted(getCoefficient(it)) },
        )

        override fun getKey() = key

        override fun getMax(holder: HudHolder) = 1.0

        override fun getPlaceholders() = placeholders

        protected fun stamina(holder: HudHolder) = StaminaBar.players[holder.player().uniqueId]
    }

    // Untracked players report full stamina so layouts hiding a full bar hide it for them too
    private object StaminaListener : StaminaLayerListener("stamina") {
        override fun getValue(holder: HudHolder) = stamina(holder)?.progress?.toDouble() ?: 1.0
    }

    // Mirrors the stamina while alerting and is empty otherwise, so a red fill bound to it covers the normal one only while alerting
    private object StaminaAlertListener : StaminaLayerListener("stamina_alert") {
        override fun getValue(holder: HudHolder) = stamina(holder)?.takeIf { it.alert }?.progress?.toDouble() ?: 0.0
    }
}
