package com.mineinabyss.staminaclimb.rope

import com.mineinabyss.geary.papermc.datastore.encodeComponentsTo
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.withGeary
import com.mineinabyss.geary.serialization.setPersisting
import com.mineinabyss.staminaclimb.modules.ropeConfig
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack

val ItemDisplay.placedPiton: PlacedPiton? get() = toGearyOrNull()?.get<PlacedPiton>()
val ItemDisplay.hangingRope: HangingRope? get() = toGearyOrNull()?.get<HangingRope>()

fun ItemDisplay.setPlacedPiton(piton: PlacedPiton) {
    val geary = toGearyOrNull() ?: return
    geary.setPersisting(piton)
    withGeary { geary.encodeComponentsTo(it) }
}

fun ItemDisplay.setHangingRope(rope: HangingRope?) {
    val geary = toGearyOrNull() ?: return
    if (rope == null) geary.remove<HangingRope>() else geary.setPersisting(rope)
    withGeary { geary.encodeComponentsTo(it) }
}

fun ItemDisplay.restartExpiry() = setExpiry(System.currentTimeMillis() + ropeConfig.pitonDespawn.inWholeMilliseconds)

fun ItemDisplay.pauseExpiry() = setExpiry(null)

private fun ItemDisplay.setExpiry(at: Long?) {
    placedPiton?.let { setPlacedPiton(it.copy(expiresAt = at)) }
}

fun HangingRope.toItemStack(): ItemStack = ItemStack.deserializeBytes(item)

fun PlacedPiton.block(world: World): Block = world.getBlockAt(blockX, blockY, blockZ)

fun PlacedPiton.faceCenter(world: World): Location =
    Location(world, blockX + 0.5, blockY + 0.5, blockZ + 0.5).add(face.direction.multiply(0.5))

/** Pushed out of the face so a climber does not stand inside the wall or ceiling, and carried onto the copy of the rope [link] leads to */
fun PlacedPiton.ropeAnchor(world: World, link: SectionLink? = null): Location {
    val anchor = faceCenter(world).add(face.direction.multiply(ropeConfig.ropeOffset))
    return link?.toTarget(anchor) ?: anchor
}

val BlockFace.isPitonFace get() = this == BlockFace.DOWN || (modY == 0 && isCartesian)
