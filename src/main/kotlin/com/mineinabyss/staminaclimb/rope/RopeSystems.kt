package com.mineinabyss.staminaclimb.rope

import com.mineinabyss.geary.modules.WorldScoped
import com.mineinabyss.geary.systems.query.query
import com.mineinabyss.idofront.time.ticks
import com.mineinabyss.staminaclimb.isFullCube
import org.bukkit.entity.ItemDisplay

/** Also drops pitons whose anchor block changed in ways no listener catches, such as commands or world edits */
fun WorldScoped.pitonExpirySystem() = system(query<PlacedPiton, ItemDisplay>()).every(20.ticks).execOnAll {
    val now = System.currentTimeMillis()
    // Collected up front, removing a piton mutates what the query iterates
    mapNotNull { (placed, display) ->
        display.takeIf { placed.expiresAt?.let { it <= now } == true || placed.lostAnchor(display) }
    }.forEach { Pitons.remove(it, dropRope = true) }
}

/** The anchor can sit in a different chunk than the piton, looking it up while that chunk is unloaded would load it */
private fun PlacedPiton.lostAnchor(display: ItemDisplay) =
    display.world.isChunkLoaded(blockX shr 4, blockZ shr 4) && !block(display.world).isFullCube
