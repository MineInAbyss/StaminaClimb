package com.mineinabyss.staminaclimb

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.idofront.plugin.isPluginEnabled
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.config.config
import com.mineinabyss.staminaclimb.stamina.StaminaBar
import kotlinx.coroutines.delay
import org.bukkit.Material
import org.bukkit.entity.Player
import org.cultofclang.bonehurtingjuice.hurtBones
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
val Player.wallDifficulty: Float
    get() {
        val loc = this.location
        //checks if player is at climbable wall
        if (loc.block.type == Material.WATER) //don't fly if in water
            return -1f

        var totalClimbDifficulty = 0f
        var climbDifficultyCount = 0
        //check for block to hang onto in a 2x2x2 area around player
        inCube(-1..1 step 2, 0..1, -1..1 step 2) { x, y, z ->
            val to = loc.clone().add(x * checkRange, y.toDouble(), z * checkRange)

            //fix some issues with half slabs by checking half a block higher when the player isn't yet climbing
            if (!ClimbBehaviour.isClimbing.containsKey(uniqueId)) to.add(0.0, 0.5, 0.0)
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
                return climbModifier * config.roofClimbDifficulty
        }
        return -1f
    }

val Material.climbDifficulty: Float
    get() = config.climbDifficulty.getOrDefault(
        this,
        config.climbDifficultyGeneral.entries.firstOrNull { (name, _) -> this.name.contains(name) }?.value
            ?: if (isSolid) 1f else -1f
    )

//TODO move to idofront
inline fun inCube(
    xRange: IntProgression,
    yRange: IntProgression,
    zRange: IntProgression,
    execute: (x: Int, y: Int, z: Int) -> Unit
) {
    for (x in xRange) for (y in yRange) for (z in zRange) execute(x, y, z)
}

var Player.climbEnabled: Boolean
    get() = uniqueId !in StaminaBar.disabledPlayers
    set(enable) {
        if (climbEnabled) {
            if (!enable) {
                StaminaBar.disabledPlayers.add(uniqueId)
                StaminaBar.unregisterBar(uniqueId)
                this.stopClimbing()
            }
        } else if (enable) {
            StaminaBar.disabledPlayers.remove(uniqueId)
            StaminaBar.registerBar(this).progress(0f)
        }
    }

fun applyClimbDamage(player: Player) {
    staminaClimb.launch {
        while (!player.location.apply { y -= 1 }.block.isSolid) {
            delay(1)
        }
        if (isPluginEnabled("BoneHurtingJuice")) {
            if (StaminaBar.fallDist.containsKey(player.uniqueId)) {
                player.hurtBones((StaminaBar.fallDist[player.uniqueId]!!.y - player.location.y).toFloat())
            }
        }
        StaminaBar.fallDist.remove(player.uniqueId)
    }
}
