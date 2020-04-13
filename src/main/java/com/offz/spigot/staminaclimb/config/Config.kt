package com.offz.spigot.staminaclimb.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.Material

@Serializable
data class Config(
        @SerialName("air-time") val AIR_TIME: Long,
        @SerialName("stamina-regen") val STAMINA_REGEN: Double,
        @SerialName("stamina-regen-in-air") val STAMINA_REGEN_IN_AIR: Double,
        @SerialName("stamina-remove-per-tick") val STAMINA_REMOVE_PER_TICK: Double,
        @SerialName("stamina-remove-while-moving") val STAMINA_REMOVE_WHILE_MOVING: Double,
        @SerialName("bar-red") val BAR_RED: Double,
        @SerialName("max-fall-dist") val MAX_FALL_DIST: Double,
        @SerialName("jump-cooldown") val JUMP_COOLDOWN: Long,
        @SerialName("roof-climb-difficulty") val ROOF_CLIMB_DIFFICULTY: Double,
        @SerialName("walljump-cooldown") val WALLJUMP_COOLDOWN: Long,
        @SerialName("climb-blacklist") val PREVENT_CLIMB_START: List<Material>,
        @SerialName("climb-blacklist-general") val PREVENT_CLIMB_START_GENERAL: List<String>,
        @SerialName("climb-difficulty") val CLIMB_DIFFICULTY: Map<Material, Double> = mapOf(),
        @SerialName("climb-difficulty-general") val CLIMB_DIFFICULTY_GENERAL: Map<String, Double> = mapOf()
)