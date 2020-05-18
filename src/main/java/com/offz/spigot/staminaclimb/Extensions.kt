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

const val checkRange = 0.4 //TODO config

//TODO move elsewhere
/** How difficult it is to climb at this location for this player. The higher, the faster stamina should drain.
 *  If lower than 0, cannot climb on this wall. */
val Player.wallDifficulty: Double
    get() {
        val loc = this.location
        //checks if player is at climbable wall
        if (loc.block.type == Material.WATER) //don't fly if in water
            return -1.0

        var totalClimbDifficulty = 0.0
        var climbDifficultyCount = 0
        //check for block to hang onto in a 2x2x2 area around player
        inCube(-1..1 step 2, 0..1, -1..1 step 2) { x, y, z ->
            val to = loc.clone().add(x * checkRange, y.toDouble(), z * checkRange)

            //fix some issues with half slabs by checking half a block higher when the player isn't yet climbing
            if (!ClimbBehaviour.isClimbing.containsKey(uniqueId)) to.add(0.0, 0.5, 0.0)
            val type = to.block.type
            val climbDifficulty = to.block.type.climbDifficulty
            if (climbDifficulty >= 0) {
                totalClimbDifficulty += climbDifficulty
                climbDifficultyCount++
            }
        }
        if (climbDifficultyCount > 0) return (totalClimbDifficulty / climbDifficultyCount)

        if ((uniqueId.isClimbing || location.direction.y > 0.5)) {
            val climbModifier = loc.clone().add(0.0, 2.2, 0.0).block.type.climbDifficulty
            if (climbModifier > 0)
                return climbModifier * climbyConfig.ROOF_CLIMB_DIFFICULTY
        }
        return -1.0
    }

val Material.climbDifficulty: Double
    get() = climbyConfig.CLIMB_DIFFICULTY.getOrDefault(this,
            climbyConfig.CLIMB_DIFFICULTY_GENERAL.entries.firstOrNull { (name, _) -> this.name.contains(name) }?.value
                    ?: if (isSolid) 1.0 else -1.0)

//TODO move to idofront
inline fun inCube(xRange: IntProgression, yRange: IntProgression, zRange: IntProgression, execute: (x: Int, y: Int, z: Int) -> Unit) {
    for (x in xRange) for (y in yRange) for (z in zRange) execute(x, y, z)
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