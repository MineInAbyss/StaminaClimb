package com.offz.spigot.staminaclimb.events

import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

enum class StaminaState {
    EXHAUSTED,
    WARNING,
    GOOD,
}

class StaminaStateChangeEvent(
    player: Player,
    val bar: BossBar,
    val enteredState: StaminaState,
) : PlayerEvent(player) {
    override fun getHandlers() = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
