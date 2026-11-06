package com.mineinabyss.staminaclimb.rope

import com.mineinabyss.staminaclimb.collidesAt
import com.mineinabyss.staminaclimb.config.SwingConfig
import com.mineinabyss.staminaclimb.modules.ropeConfig
import com.mineinabyss.staminaclimb.normalizeOrZero
import org.bukkit.Input
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.sin

/** Rebuilt every tick rather than cached at startup, so a config reload takes effect */
class SwingLimits(cfg: SwingConfig) {
    val steerCos = cos(Math.toRadians(cfg.steerAngle))
    val maxCos = -cos(Math.toRadians(cfg.maxAngle))
    val maxSin = sin(Math.toRadians(cfg.maxAngle))
    val restCos = -cos(Math.toRadians(cfg.restAngle))
}

/**
 * Spherical pendulum step: gravity and pumping move the feet, then the rope length constraint pulls them back onto the arc.
 * Returns where the feet end up
 */
fun Climber.swing(player: Player, limits: SwingLimits): Vector {
    RopeClimb.leadOf(piton)?.takeIf { it !== this }?.let { lead ->
        // Only the lead's swing tilts the rope display, so anyone else on the rope hangs along it unless that puts them in a wall
        if (!player.collidesAt(anchor.clone().add(lead.dir.clone().multiply(length)))) dir = lead.dir.clone()
        vel = Vector()
        return feet
    }

    val cfg = ropeConfig.swing
    val input = input
    val braking = input?.isJump == true && input.isSneak
    val pump = input?.takeIf { !braking && it.holdsDirection }
        ?.let { pumpVector(player, it) }
        ?.takeIf { it.lengthSquared() > 0 }
    val pumping = pump != null

    val from = feet
    if (!swinging && !pumping) return from

    val momentum = vel
    momentum.y -= cfg.gravity
    momentum.multiply(if (braking) cfg.brakeDamping else cfg.damping)

    if (pump != null) {
        // A held key pumps while moving toward it, steers once it points clearly across the swing, and does nothing while moving away
        val key = pump.normalize()
        val motion = Vector(momentum.x, 0.0, momentum.z)
        val push = if (motion.lengthSquared() < 1e-4) key else {
            val along = motion.normalize()
            val alignment = along.dot(key)
            when {
                alignment >= limits.steerCos -> along
                alignment <= -limits.steerCos -> null
                else -> key.subtract(along.multiply(alignment)).normalizeOrZero()
            }
        }
        push?.let { momentum.add(it.multiply(cfg.pumpAcceleration)) }
    }

    var dir = from.clone().add(momentum).subtract(anchor).normalizeOrZero().takeIf { it.lengthSquared() > 0 } ?: Climber.straightDown()
    var hitLimit = false
    if (dir.y > limits.maxCos) {
        val horizontal = dir.clone().setY(0).normalizeOrZero()
        // Straight up has no swing plane to clamp into, so drop back to hanging still rather than build a non unit dir
        dir = if (horizontal.lengthSquared() == 0.0) Climber.straightDown()
        else horizontal.multiply(limits.maxSin).setY(limits.maxCos)
        hitLimit = true
    }

    val to = anchor.clone().add(dir.clone().multiply(length))
    if (player.collidesAt(to)) {
        this.vel = Vector()
        return from
    }

    this.vel = if (hitLimit) Vector() else to.clone().subtract(from)
    this.dir = dir
    if (!pumping && dir.y < limits.restCos && this.vel.lengthSquared() < cfg.restSpeed * cfg.restSpeed) {
        this.dir = Climber.straightDown()
        this.vel = Vector()
        return feet
    }
    return to
}

private val Input.holdsDirection get() = isForward || isBackward || isLeft || isRight

private fun pumpVector(player: Player, input: Input): Vector {
    val forward = player.location.direction.setY(0).normalizeOrZero()
    val right = Vector(-forward.z, 0.0, forward.x)
    val pump = Vector()
    if (input.isForward) pump.add(forward)
    if (input.isBackward) pump.subtract(forward)
    if (input.isRight) pump.add(right)
    if (input.isLeft) pump.subtract(right)
    return pump
}
