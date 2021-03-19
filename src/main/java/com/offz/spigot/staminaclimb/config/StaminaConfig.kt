package com.offz.spigot.staminaclimb.config

import com.mineinabyss.idofront.config.IdofrontConfig
import com.offz.spigot.staminaclimb.staminaClimb
import kotlinx.serialization.Serializable
import org.bukkit.Material

object StaminaConfig : IdofrontConfig<StaminaConfig.Data>(staminaClimb, Data.serializer()) {
    @Serializable
    class Data(
        val airTime: Long,
        val staminaRegen: Double,
        val staminaRegenInAir: Double,
        val staminaRemovePerTick: Double,
        val staminaRemoveWhileMoving: Double,
        val barRedZone: Double,
        val barFlashPoint: Double,
        val maxFallDist: Double,
        val minMovementValue: Double,
        val jumpCooldown: Long,
        val roofClimbDifficulty: Double,
        val walljumpCooldown: Long,
        val climbBlacklist: List<Material>,
        val climbBlacklistGeneral: List<String>,
        val climbDifficulty: Map<Material, Double> = mapOf(),
        val climbDifficultyGeneral: Map<String, Double> = mapOf()
    )
}
