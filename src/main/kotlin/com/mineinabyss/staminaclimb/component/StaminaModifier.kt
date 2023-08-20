package com.mineinabyss.staminaclimb.component

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("climb:stamina_modifier")
data class StaminaModifier(val modifier: Double, val operation: ModifierOperation) {
    enum class ModifierOperation {
        ADD, MULTIPLY_BASE, MULTIPLY
    }
}
