package com.mineinabyss.staminaclimb.events
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import kotlin.math.roundToInt

/**
 * Called when a Player stops climbing
 * @param player The Player which stopped climbing
 * @param distanceClimbed The distance climbed, negative is down, positive is up
 */
class PlayerStopClimbEvent(player: Player, val distanceClimbed: Double) : PlayerEvent(player) {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }

    val blocksClimbed = distanceClimbed.roundToInt()
}