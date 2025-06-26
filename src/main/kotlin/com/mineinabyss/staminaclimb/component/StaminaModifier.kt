package com.mineinabyss.staminaclimb.component

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("climb:stamina_modifiers")
data class StaminaModifiers(val modifiers: List<StaminaModifier>)

@Serializable
data class StaminaModifier(val modifier: Double, val operation: ModifierOperation, val type: ModifierType) {
    enum class ModifierOperation {
        ADD, MULTIPLY_BASE, MULTIPLY
    }

    enum class ModifierType {
        STAMINA, CLIMB_SPEED, REGENERATE
    }
}
