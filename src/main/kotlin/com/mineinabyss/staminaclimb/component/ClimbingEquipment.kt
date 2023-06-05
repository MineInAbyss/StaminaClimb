package com.mineinabyss.staminaclimb.component

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("climb:equipment")
data class ClimbingEquipment(val modifier: Double)
