package com.mineinabyss.staminaclimb

import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.papermc.gearyPaper
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour.stopClimbing
import com.mineinabyss.staminaclimb.modules.StaminaClimbModule
import com.mineinabyss.staminaclimb.modules.StaminaPaperModule
import com.mineinabyss.staminaclimb.modules.stamina
import com.mineinabyss.staminaclimb.nms.Tags
import com.mineinabyss.staminaclimb.stamina.StaminaBar
import com.mineinabyss.staminaclimb.stamina.StaminaBar.registerBar
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/** A reference to the StaminaClimb plugin */

class StaminaClimbPlugin : JavaPlugin() {
    override fun onLoad() {
        createClimbContext()
        gearyPaper.configure {
            world.autoscan {
                scan(this@StaminaClimbPlugin.classLoader, listOf("com.mineinabyss.staminaclimb")) {
                    components()
                }
            }
        }
    }

    override fun onEnable() {
        stamina.staminaTask.runTaskTimer(this@StaminaClimbPlugin, 0, 1)

        // toggle system on for all online players (for plugin reload)
        Bukkit.getOnlinePlayers().forEach(::registerBar)

        listeners(ClimbBehaviour, StaminaBar)
        StaminaCommands.registerCommands()

        Tags.interceptConfigPhaseTagPacket()
    }

    fun createClimbContext() {
        StaminaClimbModule.instance = StaminaPaperModule(this)
    }

    override fun onDisable() {
        logger.info("onDisable has been invoked!")

        //prevent players from getting access to flight after server restart/plugin reload
        ClimbBehaviour.isClimbing.keys.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { stopClimbing(it) }
        }

        //stop stamina bars from duplicating on plugin reload
        StaminaBar.registeredBars.values.forEach { Bukkit.getServer().hideBossBar(it) }
    }
}
