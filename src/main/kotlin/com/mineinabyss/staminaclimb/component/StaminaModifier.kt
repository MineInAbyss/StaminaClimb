package com.mineinabyss.staminaclimb.component

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
@SerialName("climb:stamina_multiplier")
value class StaminaModifier(val modifier: Double)
