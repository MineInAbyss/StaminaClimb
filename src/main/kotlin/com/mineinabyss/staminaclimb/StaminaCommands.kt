package com.mineinabyss.staminaclimb

import com.mineinabyss.idofront.commands.execution.ExperimentalCommandDSL
import com.mineinabyss.idofront.commands.execution.IdofrontCommandExecutor
import com.mineinabyss.idofront.commands.extensions.actions.playerAction
import com.mineinabyss.idofront.messaging.info
import com.mineinabyss.staminaclimb.config.StaminaConfig

@ExperimentalCommandDSL
class StaminaCommands : IdofrontCommandExecutor() {
    override val commands = commands(staminaClimb) {
        "climb" {
            permission = "staminaclimb.toggle"
            playerAction {
                player.climbEnabled = !player.climbEnabled
                player.info("Stamina and climbing system: ${if (player.climbEnabled) "ON" else "OFF"}!")
            }
        }
        "staminaclimb" {
            "reload" {
                action {
                    StaminaConfig.reload(sender)
                }
            }
        }
    }

}
