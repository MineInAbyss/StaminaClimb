package com.mineinabyss.staminaclimb

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.idofront.time.ticks
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour.stopClimbing
import com.mineinabyss.staminaclimb.modules.StaminaClimbModule
import com.mineinabyss.staminaclimb.modules.StaminaPaperModule
import com.mineinabyss.staminaclimb.nms.Tags
import com.mineinabyss.staminaclimb.stamina.StaminaBar
import com.mineinabyss.staminaclimb.stamina.StaminaBar.registerBar
import com.mineinabyss.staminaclimb.stamina.StaminaTask
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/** A reference to the StaminaClimb plugin */

class StaminaClimbPlugin : JavaPlugin() {
    override fun onLoad() {
        createClimbContext()
        geary {
            autoscan(classLoader, "com.mineinabyss.staminaclimb") {
                all()
            }
        }
    }

    override fun onEnable() {
        StaminaTask().runTaskTimer(this@StaminaClimbPlugin, 0, 1)

        // toggle system on for all online players (for plugin reload)
        Bukkit.getOnlinePlayers().forEach { registerBar(it) }

        listeners(ClimbBehaviour, StaminaBar)
        StaminaCommands()

        Tags.interceptConfigPhaseTagPacket()
    }

    fun createClimbContext() {
        DI.remove<StaminaClimbModule>()
        DI.add<StaminaClimbModule>(StaminaPaperModule(this))
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
