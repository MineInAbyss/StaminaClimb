package com.mineinabyss.staminaclimb.config

import com.mineinabyss.idofront.config.IdofrontConfig
import com.mineinabyss.staminaclimb.staminaClimb
import kotlinx.serialization.Serializable
import org.bukkit.Material

object StaminaConfig : IdofrontConfig<StaminaConfig.Data>(staminaClimb, Data.serializer()) {
    @Serializable
    class Data(
        val airTime: Long,
        val staminaRegen: Float,
        val staminaRegenInAir: Float,
        val staminaRemovePerTick: Float,
        val staminaRemoveWhileMoving: Float,
        val staminaRemoveWhileOnLadder: Float,
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

    private const val SERVER_TPS = 20
    private const val NANO_IN_SECOND = 1000000000
    const val NANO_PER_TICK = NANO_IN_SECOND / SERVER_TPS
}
