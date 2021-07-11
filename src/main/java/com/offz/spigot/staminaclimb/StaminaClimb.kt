package com.offz.spigot.staminaclimb

import com.mineinabyss.idofront.commands.execution.ExperimentalCommandDSL
import com.mineinabyss.idofront.plugin.registerEvents
import com.offz.spigot.staminaclimb.climbing.ClimbBehaviour
import com.offz.spigot.staminaclimb.climbing.ClimbBehaviour.stopClimbing
import com.offz.spigot.staminaclimb.config.StaminaConfig
import com.offz.spigot.staminaclimb.stamina.StaminaBar
import com.offz.spigot.staminaclimb.stamina.StaminaBar.registerBar
import com.offz.spigot.staminaclimb.stamina.StaminaTask
import com.okkero.skedule.schedule
import io.github.slimjar.app.builder.ApplicationBuilder
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/** A reference to the StaminaClimb plugin */
val staminaClimb: StaminaClimb by lazy { JavaPlugin.getPlugin(StaminaClimb::class.java) }

class StaminaClimb : JavaPlugin() {
    @ExperimentalCommandDSL
    override fun onEnable() {
        logger.info("Downloading dependencies")
        ApplicationBuilder.appending("StaminaClimb").build()

        saveDefaultConfig()

        // toggle system on for all online players (for plugin reload)
        Bukkit.getOnlinePlayers().forEach { registerBar(it) }

        StaminaTask().startTask()

        registerEvents(
            ClimbBehaviour,
            StaminaBar,
        )

        StaminaCommands()

        // initialize singleton objects
        StaminaConfig
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
