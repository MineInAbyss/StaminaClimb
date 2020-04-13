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
        @SerialName("walljump-cooldown") val WALLJUMP_COOLDOWN: Long,
        @SerialName("climb-blacklist") val CLIMB_BLACKLIST: List<Material>,
        @SerialName("climb-blacklist-general") val CLIMB_BLACKLIST_GENERAL: List<String>
)