package com.offz.spigot.staminaclimb.stamina

import com.mineinabyss.idofront.messaging.color
import com.offz.spigot.staminaclimb.*
import com.offz.spigot.staminaclimb.climbing.ClimbBehaviour
import com.offz.spigot.staminaclimb.config.StaminaConfig
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.util.Vector
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.boss.BarColor
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs

class StaminaTask : BukkitRunnable() {

    private var previousTickTime: Long = 0

    private var ticksSinceLastColorSwitch = 0
    private val ticksToSwitchColor = 5

    private var currentWallMultiplier = 1.0

    internal fun isPlayerMoving(uuid: UUID): Boolean {
        val vel = StaminaBar.velocities[uuid] ?: return false
        return vel > StaminaConfig.data.minMovementValue
    }

    override fun run() {
        StaminaBar.registeredBars.keys.forEach { uuid ->
            val player = Bukkit.getPlayer(uuid) ?: StaminaBar.registeredBars.remove(uuid).let { return@forEach }
            val bar = StaminaBar.registeredBars[uuid] ?: return@forEach
            val progress = bar.progress
          
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
                StaminaBar.unregisterBar(uuid)
                player.stopClimbing()
                player.allowFlight = true
                return
            }

            bar.isVisible = progress + StaminaConfig.data.staminaRegen <= 1.0 //hide when full

            if (!uuid.isClimbing) { //Regenerate stamina
                if(player.isOnGround)
                    StaminaBar.addProgressWithDeltaTime(StaminaConfig.data.staminaRegen.coerceAtMost(1.0), uuid)
                else 
                    StaminaBar.addProgressWithDeltaTime(StaminaConfig.data.staminaRegenInAir.coerceAtMost(1.0), uuid)
            } else { //Lose stamina
                if(isPlayerMoving(uuid)) {
                    StaminaBar.removeProgressWithDeltaTime(StaminaConfig.data.staminaRemoveWhileMoving * currentWallMultiplier, uuid)
                }
                else {
                    StaminaBar.removeProgressWithDeltaTime(StaminaConfig.data.staminaRemovePerTick * currentWallMultiplier, uuid)
                }
            }

            if (progress <= StaminaConfig.data.barRedZone) { //Changing bar colors and effects on player depending on its progress
                bar.color = BarColor.RED
                if (uuid.isClimbing) {
                    player.stopClimbing()
                    uuid.canClimb = false //If player reaches red zone, they can't climb until they get back in green zone
                    player.addPotionEffect(PotionEffect(PotionEffectType.SLOW, 110, 2, false, false))
                    player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 110, 2, false, false))
                }
            } else if (progress < 1.0 && !uuid.canClimb) {
                bar.color = BarColor.RED //Keep Stamina Bar red even in yellow zone while it's regenerating
            } else if (uuid.isClimbing && progress < StaminaConfig.data.barFlashPoint) { //Flash green-red
                ticksSinceLastColorSwitch++
                if (ticksSinceLastColorSwitch >= if(progress < StaminaConfig.data.barFlashPoint / 2) ticksToSwitchColor / 2 else ticksToSwitchColor) {
                    if(bar.color == BarColor.RED)
                        bar.color = if(progress < StaminaConfig.data.barFlashPoint) BarColor.YELLOW else BarColor.GREEN
                    else
                        bar.color = BarColor.RED
                    ticksSinceLastColorSwitch = 0
                }
            } else {
                bar.color = BarColor.GREEN
                uuid.canClimb = true
            }
            bar.setTitle(if(!uuid.canClimb) "&c&lStamina".color() else "&lStamina".color()) //Color Stamina title
        }



        var specialWall = false

        ClimbBehaviour.isClimbing.entries.forEach { (uuid, isClimbing) ->
            val player = Bukkit.getPlayer(uuid) ?: ClimbBehaviour.isClimbing.remove(uuid).let { return }

            //if climbing in creative, stop climbing but keep flight
            if (player.gameMode == GameMode.CREATIVE) {
                player.stopClimbing()
                player.allowFlight = true
                player.isFlying = true
                return@forEach
            }

            //prevent player from climbing if they have fallen far enough TODO dunno what hte flying check is for
            if (!player.isFlying && isClimbing || player.fallDistance > StaminaConfig.data.maxFallDist) {
                player.stopClimbing()
                return@forEach
            }
            val atWallMultiplier = player.wallDifficulty
            if (atWallMultiplier >= 0) {
                if (uuid.climbCooldownDone) uuid.restartCooldown()
                player.allowFlight = true
                player.isFlying = true
                ClimbBehaviour.isClimbing[uuid] = true
            } else {
                if (isClimbing) {
                    uuid.isClimbing = false
                    uuid.restartCooldown()
                    player.launchInDirection()
                    player.isFlying = false
                    player.allowFlight = false
                }
                //only prevent air jump after AIR_TIME ms
                else if (uuid.climbCooldown + StaminaConfig.data.airTime < 0) {
                    player.stopClimbing()
                    return@forEach
                }
            }

            specialWall = true
            currentWallMultiplier = atWallMultiplier
        }

        if (!specialWall)
            currentWallMultiplier = 1.0
    }
}
