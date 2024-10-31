package com.mineinabyss.staminaclimb

import com.mineinabyss.idofront.commands.brigadier.commands
import com.mineinabyss.idofront.messaging.info
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.modules.stamina
import com.mineinabyss.staminaclimb.nms.Tags
import com.mineinabyss.staminaclimb.stamina.StaminaBar

object StaminaCommands {
    fun registerCommands() {
        stamina.plugin.commands {
            "climb" {
                requiresPermission("")
                playerExecutes {
                    player.climbEnabled = !player.climbEnabled
                    if (player.climbEnabled) Tags.enableClimb(player)
                    else Tags.disableClimb(player)
                    player.info("Stamina and climbing system: ${if (player.climbEnabled) "ON" else "OFF"}!")
                }
                "reload" {
                    requiresPermission("staminaclimb.reload")
                    executes {
                        stamina.staminaTask.cancel()
                        stamina.plugin.createClimbContext()
                        stamina.staminaTask.runTaskTimer(stamina.plugin, 0, 1)
                        StaminaBar.conf = stamina.config
                        ClimbBehaviour.conf = stamina.config
                        sender.success("Config has been reloaded!")
                    }
                }
            }
        }
    }
}
