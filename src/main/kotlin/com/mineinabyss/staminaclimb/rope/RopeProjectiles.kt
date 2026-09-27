package com.mineinabyss.staminaclimb.rope

import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.staminaclimb.isCreative
import com.mineinabyss.staminaclimb.modules.ropeConfig
import com.mineinabyss.staminaclimb.modules.stamina
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.scheduler.BukkitTask
import java.util.*

object RopeProjectiles {
    private class Tracked(val projectile: Projectile, val shooter: UUID, var from: Location)

    private val tracked = HashMap<UUID, Tracked>()
    private var task: BukkitTask? = null

    fun track(projectile: Projectile, shooter: Player) {
        tracked[projectile.uniqueId] = Tracked(projectile, shooter.uniqueId, projectile.location)
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(stamina.plugin, ::tick, 1, 1)
    }

    private fun tick() {
        tracked.values.removeIf { shot ->
            val projectile = shot.projectile.takeIf { it.isValid } ?: return@removeIf true
            val to = projectile.location.takeIf { it.world == shot.from.world } ?: return@removeIf true
            if (PitonParts.hasPitons(to.world) && hit(shot, to)) return@removeIf true
            shot.from = to
            projectile.isOnGround || (projectile as? AbstractArrow)?.isInBlock == true
        }
        if (tracked.isNotEmpty()) return
        task?.cancel()
        task = null
    }

    private fun hit(shot: Tracked, to: Location): Boolean {
        val from = shot.from
        val travel = to.toVector().subtract(from.toVector())
        val distance = travel.length().takeIf { it >= 1e-4 } ?: return false
        val hit = from.world.rayTraceEntities(from, travel, distance, PitonParts::isHitbox) ?: return false
        val hitbox = hit.hitEntity?.let(PitonParts::hitboxOf) ?: return false
        val piton = hitbox.piton.takeIf { RopeClimb.countOn(it) == 0 } ?: return false

        when {
            piton.hangingRope?.owner == shot.shooter -> {
                val item = Pitons.detachRope(piton) ?: return false
                val location = hit.hitPosition.toLocation(from.world)
                if (shot.shooter.toPlayer()?.isCreative != true) Pitons.dropItem(location, item)
                ropeConfig.sounds.detachRope.play(location)
            }
            !hitbox.isRope && piton.placedPiton?.owner == shot.shooter -> Pitons.breakPiton(piton, hitbox.isMirror)
            else -> return false
        }

        shot.projectile.remove()
        return true
    }
}
