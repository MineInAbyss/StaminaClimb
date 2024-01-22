package com.mineinabyss.staminaclimb

import com.mineinabyss.idofront.commands.execution.IdofrontCommandExecutor
import com.mineinabyss.idofront.commands.extensions.actions.playerAction
import com.mineinabyss.idofront.messaging.info
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.staminaclimb.modules.stamina
import com.mineinabyss.staminaclimb.nms.Tags
import net.minecraft.tags.BlockTags
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class StaminaCommands : IdofrontCommandExecutor(), TabCompleter {
    override val commands = commands(stamina.plugin) {
        "climb" {
            permission = "staminaclimb.toggle"
            playerAction {
                player.climbEnabled = !player.climbEnabled
                if (player.climbEnabled) Tags.enableClimb(player)
                else Tags.disableClimb(player)
                player.info("Stamina and climbing system: ${if (player.climbEnabled) "ON" else "OFF"}!")
            }
        }
        "staminaclimb" {
            "reload" {
                action {
                    stamina.configHolder.reload()
                    sender.success("Config has been reloaded!")
                }
            }
            "tags" {
                action {
                    stamina.emptyClimbableMap.entries.find { it.key == BlockTags.FALL_DAMAGE_RESETTING.location }?.let {
                        sender.info("Fall damage resetting tag: ${it.value}")
                    }
                    stamina.normalClimbableMap.entries.find { it.key == BlockTags.CLIMBABLE.location }?.let {
                        sender.info("Climbable tag: ${it.value}")
                    }
                }
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> {
        return if (command.name == "staminaclimb") {
            when (args.size) {
                1 -> listOf("reload")
                else -> emptyList()
            }
        } else emptyList()
    }

}
