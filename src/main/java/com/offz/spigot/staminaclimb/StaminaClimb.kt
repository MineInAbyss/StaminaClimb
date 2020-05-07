package com.offz.spigot.staminaclimb

import com.charleskorn.kaml.Yaml
import com.offz.spigot.staminaclimb.climbing.ClimbBehaviour
import com.offz.spigot.staminaclimb.climbing.ClimbBehaviour.stopClimbing
import com.offz.spigot.staminaclimb.config.Config
import com.offz.spigot.staminaclimb.stamina.StaminaBar
import com.offz.spigot.staminaclimb.stamina.StaminaBar.registerBar
import com.offz.spigot.staminaclimb.stamina.StaminaTask
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/** A reference to the StaminaClimb plugin */
val staminaClimb: StaminaClimb by lazy { JavaPlugin.getPlugin(StaminaClimb::class.java) }
val climbyConfig get() = staminaClimb.climbyConfig

class StaminaClimb : JavaPlugin() {
    lateinit var climbyConfig: Config private set

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (label.equals("toggleStamina", ignoreCase = true) || label.equals("climb", ignoreCase = true)) { //Stamina toggle
            if (sender.hasPermission("staminaclimb.toggle")) {
                if (sender is Player) {
                    if (!sender.climbEnabled) {
                        sender.sendMessage("Stamina and climbing system: ON!")
                        sender.climbEnabled = true
                    } else {
                        sender.sendMessage("Stamina and climbing system: OFF!")
                        sender.climbEnabled = false
                    }
                    return true
                }
            } else sender.sendMessage("You do not have the permission to use this command")
        }
        return false
    }

    override fun onEnable() {
        logger.info("On enable has been called")
        saveDefaultConfig()
        climbyConfig = Yaml.default.parse(Config.serializer(), config.saveToString())
        //toggle system on for all online players (for plugin reload)
        Bukkit.getOnlinePlayers().forEach { registerBar(it) }

        StaminaTask().runTaskTimer(this, 0, 1)
        server.pluginManager.registerEvents(ClimbBehaviour, this)
        server.pluginManager.registerEvents(StaminaBar, this)

    }

    override fun onDisable() {
        logger.info("onDisable has been invoked!")

        //prevent players from getting access to flight after server restart/plugin reload
        ClimbBehaviour.isClimbing.keys.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { stopClimbing(it) }
        }

        //stop stamina bars from duplicating on plugin reload
        StaminaBar.registeredBars.values.forEach { it.removeAll() }
    }
}