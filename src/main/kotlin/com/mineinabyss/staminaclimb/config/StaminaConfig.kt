package com.mineinabyss.staminaclimb.config

import kotlinx.serialization.Serializable
import net.kyori.adventure.bossbar.BossBar.Color
import net.kyori.adventure.bossbar.BossBar.Overlay
import org.bukkit.Material

@Serializable
class StaminaConfig(
    val airTime: Long,
    val staminaRegen: Float,
    val staminaRegenInAir: Float,
    val staminaRemovePerTick: Float,
    val staminaRemoveWhileMoving: Float,
    val staminaRemoveWhileOnLadder: Float,
    val baseBarColor: Color,
    val baseOverlay: Overlay,
    val barRed: Float,
    val barBlink1: Float,
    val barBlink2: Float,
    val barBlinkSpeed1: Float,
    val barBlinkSpeed2: Float,
    val maxFallDist: Double,
    val jumpCooldown: Long,
    val roofClimbDifficulty: Float,
    val walljumpCooldown: Long,
    val climbBlacklist: List<Material>,
    val climbBlacklistGeneral: List<String>,
    val climbDifficulty: Map<Material, Float> = mapOf(),
    val climbDifficultyGeneral: Map<String, Float> = mapOf()
)
