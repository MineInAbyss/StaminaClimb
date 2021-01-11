package com.offz.spigot.staminaclimb

import com.charleskorn.kaml.Yaml
import com.mineinabyss.idofront.commands.execution.ExperimentalCommandDSL
import com.offz.spigot.staminaclimb.climbing.ClimbBehaviour
import com.offz.spigot.staminaclimb.climbing.ClimbBehaviour.stopClimbing
import com.offz.spigot.staminaclimb.config.Config
import com.offz.spigot.staminaclimb.stamina.StaminaBar
import com.offz.spigot.staminaclimb.stamina.StaminaBar.registerBar
import com.offz.spigot.staminaclimb.stamina.StaminaTask
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/** A reference to the StaminaClimb plugin */
val staminaClimb: StaminaClimb by lazy { JavaPlugin.getPlugin(StaminaClimb::class.java) }
val climbyConfig get() = staminaClimb.climbyConfig

class StaminaClimb : JavaPlugin() {
    lateinit var climbyConfig: Config private set

    @ExperimentalCommandDSL
    override fun onEnable() {
        logger.info("On enable has been called")
        saveDefaultConfig()
        climbyConfig = Yaml.default.decodeFromString(Config.serializer(), config.saveToString())
        //toggle system on for all online players (for plugin reload)
        Bukkit.getOnlinePlayers().forEach { registerBar(it) }

        StaminaTask().runTaskTimer(this, 0, 1)
        server.pluginManager.registerEvents(ClimbBehaviour, this)
        server.pluginManager.registerEvents(StaminaBar, this)

        StaminaClimbCommands()
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
