package com.mineinabyss.staminaclimb.modules

import com.mineinabyss.idofront.config.config
import com.mineinabyss.staminaclimb.StaminaClimbPlugin
import com.mineinabyss.staminaclimb.config.StaminaConfig
import com.mineinabyss.staminaclimb.nms.Tags

class StaminaPaperModule(
    override val plugin: StaminaClimbPlugin
) : StaminaClimbModule {
    override val config: StaminaConfig by config("config") { plugin.fromPluginPath(loadDefault = true) }
    override val emptyClimbableMap = Tags.createEmptyClimbableMap()
    override val normalClimbableMap = Tags.createNormalClimbableMap()
}
