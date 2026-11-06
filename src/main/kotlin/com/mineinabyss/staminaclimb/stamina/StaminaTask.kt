package com.mineinabyss.staminaclimb.stamina

import com.mineinabyss.geary.papermc.tracking.items.inventory.toGeary
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.idofront.nms.aliases.toNMS
import com.mineinabyss.idofront.time.inWholeTicks
import com.mineinabyss.staminaclimb.*
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.component.StaminaModifier
import com.mineinabyss.staminaclimb.config.StaminaConfig
import org.bukkit.GameMode
import org.bukkit.scheduler.BukkitRunnable
import kotlin.time.Duration.Companion.nanoseconds

class StaminaTask(private val config: StaminaConfig) : BukkitRunnable() {
    private var lastTickNano = System.nanoTime()
    private var timeSinceLastColorFlip = 0L
    private var lastTime = System.currentTimeMillis()

    override fun run() {
        val currentNano = System.nanoTime()
        val tickDuration = (currentNano - lastTickNano).nanoseconds.inWholeTicks
        lastTickNano = currentNano

        StaminaBar.forEachPlayer { player, uuid, stamina ->
            val progress = stamina.progress

            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
                StaminaBar.unregisterBar(uuid)
                player.stopClimbing()
                player.allowFlight = true
                return
            }

            //regenerate stamina
            if (!uuid.isClimbing && !ClimbBehaviour.isGrabSliding(uuid)) {
                val base = when {
                    player.toNMS().onGround() -> config.staminaRegen
                    !player.isInClimbableBlock -> config.staminaRegenInAir
                    else -> 0f
                }

                val gearyInventory = player.inventory.toGeary()
                val amount = gearyInventory?.equipmentModifiers(base, StaminaModifier.ModifierType.REGENERATE) ?: base
                player.addStamina(amount)
            }

            when {
                progress <= config.barRed -> { //Changing bar colors and effects on player depending on its progress
                    StaminaBar.update(player, stamina, alert = true)
                    if (uuid.isClimbing) player.stopClimbing()

                    uuid.canClimb = false //If player is in red zone, they can't climb until they get back in green zone
                    player.addPotionEffect(SLOWNESS_EFFECT)
                    player.addPotionEffect(WEAKNESS_EFFECT)
                }

                progress < 1 && !uuid.canClimb -> StaminaBar.update(player, stamina, alert = true) //Keep Stamina Bar red even in yellow zone while it's regenerating
                (uuid.isClimbing || player.isInClimbableBlock) && progress <= config.barBlink2 -> {
                    val deltaTime = System.currentTimeMillis() - lastTime
                    lastTime = System.currentTimeMillis()
                    if (timeSinceLastColorFlip < config.barBlinkSpeed2)
                        timeSinceLastColorFlip += deltaTime
                    else {
                        StaminaBar.update(player, stamina, alert = !stamina.alert)
                        timeSinceLastColorFlip = 0
                    }
                }

                (uuid.isClimbing || player.isInClimbableBlock) && progress <= config.barBlink1 -> {
                    val deltaTime = System.currentTimeMillis() - lastTime
                    lastTime = System.currentTimeMillis()
                    if (timeSinceLastColorFlip < config.barBlinkSpeed1)
                        timeSinceLastColorFlip += deltaTime
                    else {
                        StaminaBar.update(player, stamina, alert = !stamina.alert)
                        timeSinceLastColorFlip = 0
                    }
                }

                else -> {
                    StaminaBar.update(player, stamina, alert = false)
                    uuid.canClimb = true
                }
            }
        }

        ClimbBehaviour.tickGrabSlides()

        ClimbBehaviour.isClimbing.entries.forEach { (uuid, isClimbing) ->
            val player = uuid.toPlayer() ?: run {
                ClimbBehaviour.isClimbing.remove(uuid)
                return@forEach
            }
            if (isClimbing) ClimbBehaviour.trackClimbMovement(player)
            //if climbing in creative, stop climbing but keep flight
            if (player.gameMode == GameMode.CREATIVE) {
                player.stopClimbing()
                player.allowFlight = true
                player.isFlying = true
                return@forEach
            }

            //prevent player from climbing if in a invalid state
            if (!player.isFlying && isClimbing) {
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

            val baseReduction = (tickDuration * config.staminaRemovePerTick * atWallMultiplier).coerceAtLeast(0.0f)
            val modifiedReduction = player.inventory.toGeary()?.let { geary ->
                val modifier = geary.equipmentModifiers(1.0f, StaminaModifier.ModifierType.STAMINA)
                baseReduction / modifier.coerceAtLeast(0.1f) // Divide to reduce stamina drain
            } ?: baseReduction
            val stamina = -modifiedReduction.coerceIn(0.0f, 1.0f)

            if (isClimbing) player.addStamina(stamina)

        }
    }
}
