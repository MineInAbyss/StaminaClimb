package com.mineinabyss.staminaclimb.rope

import com.mineinabyss.idofront.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.block.BlockFace
import java.util.*

@Serializable
@SerialName("climb:piton")
data class Piton(val fusing: Boolean = false)

@Serializable
@SerialName("climb:rope")
class Rope

/** [expiresAt] is epoch millis, null while someone hangs on its rope */
@Serializable
@SerialName("climb:placed_piton")
data class PlacedPiton(
    val owner: @Serializable(UUIDSerializer::class) UUID,
    val face: BlockFace,
    val blockX: Int,
    val blockY: Int,
    val blockZ: Int,
    val fusing: Boolean = false,
    val expiresAt: Long? = null,
)

/** [item] is the consumed rope stack as bytes */
@Serializable
@SerialName("climb:hanging_rope")
class HangingRope(
    val owner: @Serializable(UUIDSerializer::class) UUID,
    val item: ByteArray,
    val length: Int,
)
