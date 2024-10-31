package com.mineinabyss.staminaclimb

import com.mineinabyss.geary.addons.dsl.createAddon
import com.mineinabyss.geary.autoscan.autoscan

val StaminaAddon = createAddon("StaminaClimb", configuration = {
    autoscan(StaminaClimbPlugin::class.java.classLoader, "com.mineinabyss.staminaclimb") {
        all()
    }
})