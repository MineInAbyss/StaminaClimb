package com.mineinabyss.staminaclimb.stamina

import org.bukkit.entity.Player
import java.util.*

interface StaminaDisplay {
    fun show(player: Player, stamina: PlayerStamina)

    fun update(player: Player, stamina: PlayerStamina)

    fun hide(uuid: UUID)

    fun hideAll()
}
