package com.mineinabyss.staminaclimb.rope

import com.mineinabyss.staminaclimb.modules.ropeConfig
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable

class RopeClimbTask : BukkitRunnable() {
    override fun run() {
        if (RopeClimb.climbers.isEmpty()) return
        val limits = SwingLimits(ropeConfig.swing)
        RopeClimb.refreshLeads()
        RopeClimb.climbers.toList().forEach { climber ->
            val player = Bukkit.getPlayer(climber.player) ?: return@forEach RopeClimb.detach(climber.player, null)
            if (!climber.refreshPiton() || player.vehicle != climber.mount) {
                RopeClimb.exit(player)
                return@forEach
            }

            val wasSwinging = climber.swinging
            if (!climber.updateLength(player)) return@forEach
            climber.moveMount(player, climber.swing(player, limits))
            RopeClimb.crossSections(player, climber)
            if (climber.slideSpeed == 0.0) player.fallDistance = 0f
            if ((climber.swinging || wasSwinging) && RopeClimb.leadsRope(climber)) PitonParts.setRopeSwing(climber.piton, climber.swingDir)
        }
    }
}
