package com.mineinabyss.staminaclimb.stamina

import com.mineinabyss.geary.papermc.tracking.items.inventory.toGeary
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.idofront.location.down
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.idofront.time.inWholeTicks
import com.mineinabyss.staminaclimb.*
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.config.StaminaConfig
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.GameMode
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import kotlin.time.Duration.Companion.nanoseconds

class StaminaTask(private val config: StaminaConfig) : BukkitRunnable() {
    private var lastTickNano = System.nanoTime()
    private var timeSinceLastColorFlip = 0L
    private var lastTime = System.currentTimeMillis()

    private val redBar = "<red><b>Stamina".miniMsg()
    private val baseBar = "<b>Stamina".miniMsg()

    override fun run() {
        val currentNano = System.nanoTime()
        val tickDuration = (currentNano - lastTickNano).nanoseconds.inWholeTicks
        lastTickNano = currentNano

        StaminaBar.forEachBar { player, uuid, bar ->
            val progress = bar.progress()

            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
                StaminaBar.unregisterBar(uuid)
                player.stopClimbing()
                player.allowFlight = true
                return
            }

            //regenerate stamina for BossBar
            if (!uuid.isClimbing)
                bar.addProgress(
                    when {
                        player.location.down(0.0625).block.isSolid -> config.staminaRegen
                        !player.isInClimbableBlock -> config.staminaRegenInAir
                        else -> 0f
                    }
                )

            when {
                progress <= config.barRed -> { //Changing bar colors and effects on player depending on its progress
                    bar.color(BossBar.Color.RED)
                    bar.name(redBar)
                    if (uuid.isClimbing) player.stopClimbing()

                    uuid.canClimb = false//If player is in red zone, they can't climb until they get back in green zone
                    player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 110, 2, false, false))
                    player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 110, 2, false, false))
                }

                progress < 1 && !uuid.canClimb -> bar.color(BossBar.Color.RED) //Keep Stamina Bar red even in yellow zone while it's regenerating
                (uuid.isClimbing || player.isInClimbableBlock) && progress <= config.barBlink2 -> {
                    val deltaTime = System.currentTimeMillis() - lastTime
                    lastTime = System.currentTimeMillis()
                    if (timeSinceLastColorFlip < config.barBlinkSpeed2)
                        timeSinceLastColorFlip += deltaTime
                    else {
                        flipColor(bar)
                        timeSinceLastColorFlip = 0
                    }
                }

                (uuid.isClimbing || player.isInClimbableBlock) && progress <= config.barBlink1 -> {
                    val deltaTime = System.currentTimeMillis() - lastTime
                    lastTime = System.currentTimeMillis()
                    if (timeSinceLastColorFlip < config.barBlinkSpeed1)
                        timeSinceLastColorFlip += deltaTime
                    else {
                        flipColor(bar)
                        timeSinceLastColorFlip = 0
                    }
                }

                else -> {
                    bar.color(config.baseBarColor)
                    bar.name(baseBar)
                    uuid.canClimb = true
                }
            }
        }

        ClimbBehaviour.isClimbing.entries.forEach { (uuid, isClimbing) ->
            val player = uuid.toPlayer() ?: ClimbBehaviour.isClimbing.remove(uuid).let { return }
            //if climbing in creative, stop climbing but keep flight
            if (player.gameMode == GameMode.CREATIVE) {
                player.stopClimbing()
                player.allowFlight = true
                player.isFlying = true
                return@forEach
            }

            //prevent player from climbing if they have fallen far enough or in a invalid state
            if (!player.isFlying && isClimbing || player.fallDistance > config.maxFallDist) {
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
                else if (uuid.climbCooldown + config.airTime < 0) {
                    player.stopClimbing()
                    return@forEach
                }
            }

            val stamina = (-tickDuration * config.staminaRemovePerTick * atWallMultiplier).let { base ->
                player.inventory.toGeary()?.getEquipmentModifiers(base) ?: base
            }

            if (isClimbing) player.addStamina(stamina)

        }
    }

    private fun flipColor(bar: BossBar) {
        when (BossBar.Color.RED) {
            bar.color() -> {
                bar.color(config.baseBarColor)
                bar.name(baseBar)
            }

            else -> {
                bar.color(BossBar.Color.RED)
                bar.name(redBar) //Make Stamina title red
            }
        }
        bar.overlay(config.baseOverlay)
    }
}
