package com.offz.spigot.staminaclimb

import com.offz.spigot.staminaclimb.climbing.ClimbBehaviour
import com.offz.spigot.staminaclimb.stamina.StaminaBar
import org.bukkit.Material
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import java.util.*

var UUID.isClimbing: Boolean
    get() = ClimbBehaviour.isClimbing[this] ?: false
    set(value) {
        ClimbBehaviour.isClimbing[this] = value
    }

var UUID.canClimb: Boolean
    get() = ClimbBehaviour.canClimb[this] ?: false
    set(value) {
        ClimbBehaviour.canClimb[this] = value
    }

fun Player.stopClimbing() = ClimbBehaviour.stopClimbing(this)

val UUID.climbCooldownDone: Boolean get() = climbCooldown < 0

var UUID.climbCooldown: Long
    get() = ClimbBehaviour.cooldown[this]?.minus(System.currentTimeMillis()) ?: 0
    set(value) {
        ClimbBehaviour.cooldown[this] = value + System.currentTimeMillis()
    }

fun UUID.restartCooldown() {
    ClimbBehaviour.cooldown[this] = System.currentTimeMillis()
}

fun Player.launchInDirection() {
    val direction = location.direction
    velocity = velocity.apply {
        x += direction.x / 20
        z += direction.z / 20
    }
}

/** How fast stamina should decay at this wall. If lower than 0, cannot climb on this wall. */
val Player.atWall: Double
    get() {
        val loc = location
        //checks if player is at climbable wall
        if (loc.block.type == Material.WATER) //don't fly if in water
            return -1.0

        //check for block to hang onto in a 2x2x2 area around player
        for (x in -1..1 step 2) {
            for (y in 0..1) {
                for (z in -1..1 step 2) {
                    val checkRange = 0.4
                    val to = loc.clone().add(x * checkRange, y.toDouble(), z * checkRange)

                    //fix some issues with half slabs by checking half a block higher when the player isn't yet climbing
                    if (!ClimbBehaviour.isClimbing.containsKey(uniqueId)) to.add(0.0, 0.5, 0.0)
                    if (to.block.type.isSolid) return 1.0
                }
            }
        }
        if ((uniqueId.isClimbing || location.direction.y > 0.5) && location.clone().add(0.0, 2.2, 0.0).block.type != Material.AIR)
            return 7.0
        return -1.0
    }

fun BossBar.removeProgress(amount: Double) = StaminaBar.removeProgress(amount, this)

fun UUID.removeProgress(amount: Double) = StaminaBar.removeProgress(amount, this)

var Player.climbEnabled: Boolean
    get() = !StaminaBar.disabledPlayers.contains(uniqueId)
    set(enable) {
        if (climbEnabled) {
            if (!enable) {
                StaminaBar.disabledPlayers.add(uniqueId)
                StaminaBar.unregisterBar(uniqueId)
                this.stopClimbing()
            }
        } else if (enable) {
            StaminaBar.disabledPlayers.remove(uniqueId)
            StaminaBar.registerBar(this)
        }
    }