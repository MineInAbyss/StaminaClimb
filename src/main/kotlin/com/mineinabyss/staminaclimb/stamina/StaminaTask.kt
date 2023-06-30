package com.mineinabyss.staminaclimb.stamina

import com.mineinabyss.geary.papermc.tracking.items.inventory.toGeary
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.idofront.time.inWholeTicks
import com.mineinabyss.staminaclimb.*
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.modules.stamina
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Tag
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import kotlin.time.Duration.Companion.nanoseconds

class StaminaTask : BukkitRunnable() {
    private val conf = stamina.config
    private var lastTickNano = System.nanoTime()
    private var timeSinceLastColorFlip = 0L
    private var lastTime = System.currentTimeMillis()

    override fun run() {
        val currentNano = System.nanoTime()
        val tickDuration = (currentNano - lastTickNano).nanoseconds.inWholeTicks
        lastTickNano = currentNano

        StaminaBar.forEachBar { player, uuid, bar ->
            val progress = bar.progress()
            val onClimbable: Boolean = Tag.CLIMBABLE.isTagged(player.location.block.type)

            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
                StaminaBar.unregisterBar(uuid)
                player.stopClimbing()
                player.allowFlight = true
                return
            }

            //regenerate stamina for BossBar
            if (!uuid.isClimbing)
                bar.addProgress(
                    if (player.location.apply { y -= 0.0625 }.block.isSolid)
                        conf.staminaRegen
                    else if (!onClimbable) conf.staminaRegenInAir else 0f
                )

            if (progress <= conf.barRed) { //Changing bar colors and effects on player depending on its progress
                bar.color(BossBar.Color.RED)
                bar.name("<red><b>Stamina".miniMsg())
                if (uuid.isClimbing) player.stopClimbing()

                uuid.canClimb = false //If player reaches red zone, they can't climb until they get back in green zone
                player.addPotionEffect(
                    PotionEffect(PotionEffectType.SLOW, 110, 2, false, false)
                )
                player.addPotionEffect(
                    PotionEffect(PotionEffectType.WEAKNESS, 110, 2, false, false)
                )
            } else if (progress < 1 && !uuid.canClimb) {
                bar.color(BossBar.Color.RED) //Keep Stamina Bar red even in yellow zone while it's regenerating
            } else if ((uuid.isClimbing || onClimbable) && progress <= conf.barBlink2) {
                val deltaTime = System.currentTimeMillis() - lastTime
                lastTime = System.currentTimeMillis()
                if (timeSinceLastColorFlip < conf.barBlinkSpeed2)
                    timeSinceLastColorFlip += deltaTime
                else {
                    flipColor(bar)
                    timeSinceLastColorFlip = 0
                }
            } else if ((uuid.isClimbing || onClimbable) && progress <= conf.barBlink1) {
                val deltaTime = System.currentTimeMillis() - lastTime
                lastTime = System.currentTimeMillis()
                if (timeSinceLastColorFlip < conf.barBlinkSpeed1)
                    timeSinceLastColorFlip += deltaTime
                else {
                    flipColor(bar)
                    timeSinceLastColorFlip = 0
                }
            } else {
                bar.color(conf.baseBarColor)
                bar.name("<b>Stamina".miniMsg())
                uuid.canClimb = true
            }
        }

        ClimbBehaviour.isClimbing.entries.forEach { (uuid, isClimbing) ->
            val player = Bukkit.getPlayer(uuid) ?: ClimbBehaviour.isClimbing.remove(uuid).let { return }
            //if climbing in creative, stop climbing but keep flight
            if (player.gameMode == GameMode.CREATIVE) {
                player.stopClimbing()
                player.allowFlight = true
                player.isFlying = true
                return@forEach
            }

            //prevent player from climbing if they have fallen far enough or in a invalid state
            if (!player.isFlying && isClimbing || player.fallDistance > conf.maxFallDist) {
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
                else if (uuid.climbCooldown + conf.airTime < 0) {
                    player.stopClimbing()
                    return@forEach
                }
            }

            val equipmentModifiers = player.inventory.toGeary()?.equipmentModifiers ?: 1f

            if (isClimbing) player.addStamina(-tickDuration * conf.staminaRemovePerTick * atWallMultiplier / equipmentModifiers)

        }
    }

    private fun flipColor(bar: BossBar) {
        if (bar.color() == BossBar.Color.RED) {
            bar.color(conf.baseBarColor)
            bar.overlay(conf.baseOverlay)
            bar.name("<b>Stamina".miniMsg())
        } else {
            bar.color(BossBar.Color.RED)
            bar.name("<red><b>Stamina".miniMsg()) //Make Stamina title red
            bar.overlay(conf.baseOverlay)
        }
    }
}
