package com.mineinabyss.staminaclimb.Events
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
class PlayerClimbEvent(player: Player) : PlayerEvent(player) {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
class PlayerStopClimbEvent(player: Player, val climbedBlocks: Int, val distanceUp: Double, val distanceDown: Double) : PlayerEvent(player) {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}