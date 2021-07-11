package com.offz.spigot.staminaclimb.systems

import com.mineinabyss.geary.ecs.api.entities.GearyEntity
import com.mineinabyss.geary.ecs.api.systems.TickingSystem
import com.mineinabyss.geary.minecraft.access.geary
import com.mineinabyss.idofront.events.call
import com.mineinabyss.idofront.messaging.color
import com.offz.spigot.staminaclimb.components.DenyClimb
import com.offz.spigot.staminaclimb.components.Stamina
import com.offz.spigot.staminaclimb.events.StaminaStateChangeEvent
import com.offz.spigot.staminaclimb.events.StaminaState
import org.bukkit.boss.BarColor
import org.bukkit.entity.Player
import org.bukkit.event.Listener

class StaminaDisplaySystem : TickingSystem(), Listener {
    private val stamina by get<Stamina>()
    private val player by get<Player>()

    override fun GearyEntity.tick() {
        val bar = stamina.bar
        val progress = stamina.progress
        bar.progress = progress

        bar.isVisible = progress >= 1
        if(!bar.isVisible) return

        if (progress <= 0 && stamina.state != StaminaState.EXHAUSTED) {
            StaminaStateChangeEvent(player, bar, StaminaState.EXHAUSTED).call()
        } else if(progress == 1.0) {
            StaminaStateChangeEvent(player, bar, StaminaState.GOOD).call()
        }
    }

    fun StaminaStateChangeEvent.changeColours() {
        when(enteredState) {
            StaminaState.EXHAUSTED -> {
                bar.color = BarColor.RED
                bar.setTitle("&c&lStamina".color()) //Make Stamina title red
            }
            StaminaState.WARNING -> {

            }
            StaminaState.GOOD -> {
                bar.color = BarColor.GREEN
                bar.setTitle("&lStamina".color())
            }
        }
    }

    fun StaminaStateChangeEvent.preventClimbing() {
        when(enteredState) {
            StaminaState.EXHAUSTED -> geary(player).add<DenyClimb>()
            else -> geary(player).remove<DenyClimb>()
        }
    }
}
