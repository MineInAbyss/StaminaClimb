package com.mineinabyss.staminaclimb

import com.mineinabyss.idofront.platforms.IdofrontPlatforms
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour.stopClimbing
import com.mineinabyss.staminaclimb.config.StaminaConfig
import com.mineinabyss.staminaclimb.nms.Tags
import com.mineinabyss.staminaclimb.stamina.StaminaBar
import com.mineinabyss.staminaclimb.stamina.StaminaBar.registerBar
import com.mineinabyss.staminaclimb.stamina.StaminaTask
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.resources.ResourceLocation
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/** A reference to the StaminaClimb plugin */
val staminaClimb: StaminaClimbPlugin by lazy { JavaPlugin.getPlugin(StaminaClimbPlugin::class.java) }
var emptyClimbableMap = mapOf<ResourceLocation, IntArrayList>()
var normalClimbableMap = mapOf<ResourceLocation, IntArrayList>()
var fallDamageResetMap = mapOf<ResourceLocation, IntArrayList>()

class StaminaClimbPlugin : JavaPlugin() {
    override fun onLoad() {
        IdofrontPlatforms.load(this, "mineinabyss")
    }

    override fun onEnable() {
        logger.info("On enable has been called")
        saveDefaultConfig()

        StaminaConfig.load()

        // toggle system on for all online players (for plugin reload)
        Bukkit.getOnlinePlayers().forEach { registerBar(it) }

        StaminaTask().runTaskTimer(this, 0, 1)
        server.pluginManager.registerEvents(ClimbBehaviour, this)
        server.pluginManager.registerEvents(StaminaBar, this)

        StaminaCommands()

        emptyClimbableMap = Tags.createEmptyClimbableMap()
        normalClimbableMap = Tags.createNormalClimbableMap()

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
