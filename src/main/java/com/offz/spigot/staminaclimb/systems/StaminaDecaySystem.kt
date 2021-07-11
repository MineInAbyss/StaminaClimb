package com.offz.spigot.staminaclimb.systems

import com.mineinabyss.geary.ecs.api.entities.GearyEntity
import com.mineinabyss.geary.ecs.api.systems.TickingSystem
import com.offz.spigot.staminaclimb.components.Climbing
import com.offz.spigot.staminaclimb.components.Stamina
import com.offz.spigot.staminaclimb.config.StaminaConfig
import org.bukkit.GameMode
import org.bukkit.entity.Player

class StaminaDecaySystem : TickingSystem() {
    val stamina by get<Stamina>()
    val player by get<Player>()
    val climbing = has<Climbing>()

    override fun GearyEntity.tick() {
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
            remove<Stamina>()
            remove<Climbing>()
            player.allowFlight = true
            return
        }
        if(player.isong)
        stamina.amount -= StaminaConfig.data.
    }
}
