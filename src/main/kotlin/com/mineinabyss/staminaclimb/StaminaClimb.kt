package com.mineinabyss.staminaclimb


import com.mineinabyss.idofront.commands.execution.ExperimentalCommandDSL
import com.mineinabyss.idofront.slimjar.IdofrontSlimjar
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour.stopClimbing
import com.mineinabyss.staminaclimb.config.StaminaConfig
import com.mineinabyss.staminaclimb.stamina.StaminaBar
import com.mineinabyss.staminaclimb.stamina.StaminaBar.registerBar
import com.mineinabyss.staminaclimb.stamina.StaminaTask
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/** A reference to the StaminaClimb plugin */
val staminaClimb: StaminaClimb by lazy { JavaPlugin.getPlugin(StaminaClimb::class.java) }

class StaminaClimb : JavaPlugin() {
    @ExperimentalCommandDSL
    override fun onEnable() {
        logger.info("On enable has been called")
        IdofrontSlimjar.loadToLibraryLoader(this)
        saveDefaultConfig()

        // toggle system on for all online players (for plugin reload)
        Bukkit.getOnlinePlayers().forEach { registerBar(it) }

        StaminaTask().runTaskTimer(this, 0, 1)
        server.pluginManager.registerEvents(ClimbBehaviour, this)
        server.pluginManager.registerEvents(StaminaBar, this)

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
