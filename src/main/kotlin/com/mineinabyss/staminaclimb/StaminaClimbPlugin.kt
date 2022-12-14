package com.mineinabyss.staminaclimb

import com.mineinabyss.idofront.config.IdofrontConfig
import com.mineinabyss.idofront.config.config
import com.mineinabyss.idofront.platforms.Platforms
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour.stopClimbing
import com.mineinabyss.staminaclimb.config.StaminaConfig
import com.mineinabyss.staminaclimb.modules.StaminaClimbModule
import com.mineinabyss.staminaclimb.modules.StaminaPaperModule
import com.mineinabyss.staminaclimb.nms.Tags
import com.mineinabyss.staminaclimb.stamina.StaminaBar
import com.mineinabyss.staminaclimb.stamina.StaminaBar.registerBar
import com.mineinabyss.staminaclimb.stamina.StaminaTask
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.resources.ResourceLocation
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/** A reference to the StaminaClimb plugin */

class StaminaClimbPlugin : JavaPlugin() {
    override fun onLoad() {
        Platforms.load(this, "mineinabyss")
    }

    override fun onEnable() {
        // toggle system on for all online players (for plugin reload)
        Bukkit.getOnlinePlayers().forEach { registerBar(it) }

        StaminaTask().runTaskTimer(this, 0, 1)
        listeners(ClimbBehaviour, StaminaBar)
        StaminaCommands()
        //TODO DI inject
        val module = StaminaPaperModule(this)

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
