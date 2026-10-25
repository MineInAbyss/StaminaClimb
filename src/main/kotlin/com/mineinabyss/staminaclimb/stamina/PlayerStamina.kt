package com.mineinabyss.staminaclimb.stamina

class PlayerStamina(progress: Float = 1f) {
    @Volatile
    var progress: Float = progress
        internal set

    /** If the Stamina-Bar is red, either from running out of stamina, or close to running out */
    @Volatile
    var alert: Boolean = false
        internal set
}
