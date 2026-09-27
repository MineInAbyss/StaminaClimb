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
import com.mineinabyss.staminaclimb.rope.FusingPitonListener
import com.mineinabyss.staminaclimb.rope.PitonListener
import com.mineinabyss.staminaclimb.rope.PitonParts
import com.mineinabyss.staminaclimb.rope.RopeClimb
import com.mineinabyss.staminaclimb.rope.RopeClimbListener
import com.mineinabyss.staminaclimb.rope.RopeMirrors
import com.mineinabyss.staminaclimb.rope.pitonExpirySystem
import com.mineinabyss.staminaclimb.stamina.StaminaBar
import com.mineinabyss.staminaclimb.stamina.StaminaBar.registerBar
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/** A reference to the StaminaClimb plugin */

class StaminaClimbPlugin : JavaPlugin() {
    private var ropeSystems: AutoCloseable? = null

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
        startTasks()
        StaminaBar.selectDisplay()

        // toggle system on for all online players (for plugin reload)
        Bukkit.getOnlinePlayers().forEach(::registerBar)

        listeners(ClimbBehaviour, StaminaBar, PitonListener, RopeClimbListener, FusingPitonListener, RopeMirrors)
        StaminaCommands.registerCommands()

        Tags.interceptConfigPhaseTagPacket()

        ropeSystems = gearyPaper.configure { pitonExpirySystem() }
        PitonParts.restoreLoaded()
        Bukkit.getOnlinePlayers().forEach(RopeClimb::rejoin)
    }

    fun createClimbContext() {
        StaminaClimbModule.instance = StaminaPaperModule(this)
    }

    fun startTasks() {
        stamina.staminaTask.runTaskTimer(this, 0, 1)
        stamina.ropeTask.runTaskTimer(this, 0, 1)
    }

    fun stopTasks() {
        stamina.staminaTask.cancel()
        stamina.ropeTask.cancel()
    }

    override fun onDisable() {
        logger.info("onDisable has been invoked!")

        //prevent players from getting access to flight after server restart/plugin reload
        ClimbBehaviour.isClimbing.keys.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { stopClimbing(it) }
        }

        RopeClimb.exitAll()
        FusingPitonListener.refundAll()
        PitonParts.clear()
        ropeSystems?.close()

        //stop stamina bars from duplicating on plugin reload
        StaminaBar.disableDisplay()
    }
}
