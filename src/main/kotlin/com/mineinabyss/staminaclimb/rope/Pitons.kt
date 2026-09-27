package com.mineinabyss.staminaclimb.rope

import com.mineinabyss.staminaclimb.hasCollision
import com.mineinabyss.staminaclimb.isFullCube
import com.mineinabyss.staminaclimb.modules.ropeConfig
import com.nexomc.protectionlib.ProtectionLib
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

sealed interface PlacementCheck {
    data object Ok : PlacementCheck
    data object Silent : PlacementCheck
    class Error(val message: String) : PlacementCheck
}

enum class RopeAttach { ATTACHED, NO_SPACE, CANCELLED }

object Pitons {
    // A player stands 1.8 blocks tall, a shorter rope would hold their head inside the block above
    private const val MIN_ROPE_LENGTH = 2

    // Nudges the piton off its face into the spot block, so a face on a chunk border does not leave the piton in another chunk than its hitbox and rope
    private const val FACE_NUDGE = 0.001

    fun checkPlacement(block: Block, face: BlockFace): PlacementCheck {
        val messages = ropeConfig.messages
        return when {
            !block.getRelative(face).isEmpty -> PlacementCheck.Silent
            face == BlockFace.UP -> PlacementCheck.Error(messages.pitonTopFace)
            !face.isPitonFace || !block.isFullCube -> PlacementCheck.Error(messages.pitonNotSolid)
            find(block, face) != null -> PlacementCheck.Error(messages.pitonOccupied)
            RopeSections.inTransition(block.getRelative(face).location) -> PlacementCheck.Error(messages.pitonInTransition)
            else -> PlacementCheck.Ok
        }
    }

    fun mayPlace(player: Player, block: Block, face: BlockFace) = ProtectionLib.canBuild(player, block.getRelative(face).location)

    fun find(block: Block, face: BlockFace): ItemDisplay? =
        PitonParts.pitonsOn(block).firstOrNull { it.placedPiton?.face == face }

    /** Null when the piton state could not be stored on the display, which is then removed again so the caller can refund the item */
    fun spawn(block: Block, face: BlockFace, item: ItemStack, owner: UUID, fusing: Boolean): ItemDisplay? {
        val placed = PlacedPiton(
            owner = owner,
            face = face,
            blockX = block.x,
            blockY = block.y,
            blockZ = block.z,
            fusing = fusing,
            expiresAt = System.currentTimeMillis() + ropeConfig.pitonDespawn.inWholeMilliseconds,
        )

        // Facing out of the block like an item frame, so FIXED models render as they would in one
        val location = placed.faceCenter(block.world).add(face.direction.multiply(FACE_NUDGE)).setDirection(face.direction)
        val display = block.world.spawn(location, ItemDisplay::class.java) {
            it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
            it.setItemStack(item.asOne())
        }

        display.setPlacedPiton(placed)
        if (display.placedPiton == null) {
            display.remove()
            return null
        }

        PitonParts.ensure(display, loadMirror = true)
        return display
    }

    fun load(display: ItemDisplay) {
        val placed = display.placedPiton ?: return
        PitonParts.ensure(display)
        // A crash while someone hung on the rope leaves the expiry paused in the persisted state
        if (placed.expiresAt == null && RopeClimb.countOn(display) == 0) display.restartExpiry()
    }

    fun remove(display: ItemDisplay, dropRope: Boolean, viaMirror: Boolean = false) {
        if (!display.isValid) return
        RopeClimb.playersOn(display).forEach(RopeClimb::exit)

        val location = PitonParts.locationOf(display, viaMirror)
        val rope = display.hangingRope?.takeIf { dropRope }?.toItemStack()
        if (rope != null) dropItem(location, rope)

        PitonParts.remove(display)
        display.remove()
    }

    fun dropItem(location: Location, item: ItemStack) {
        val at = RopeSections.dropLocation(location)
        at.world.dropItem(at, item)
    }

    fun dropAllOn(block: Block) = PitonParts.pitonsOn(block).forEach {
        remove(it, dropRope = true, viaMirror = !it.isAnchoredOn(block))
    }

    private fun ItemDisplay.isAnchoredOn(block: Block) =
        world == block.world && placedPiton?.let { it.blockX == block.x && it.blockY == block.y && it.blockZ == block.z } == true

    fun breakPiton(display: ItemDisplay, viaMirror: Boolean = false) {
        val location = PitonParts.locationOf(display, viaMirror)
        remove(display, dropRope = true, viaMirror)
        ropeConfig.sounds.breakPiton.play(location)
    }

    /** Past the bottom of the anchor's section the rope carries on in the overlapping section below, whose chunk is loaded first */
    suspend fun ropeLength(anchor: Location): Int {
        val maxLength = ropeConfig.maxRopeLength
        val region = RopeSections.regionAt(anchor)
        val below = RopeSections.linkBelow(anchor)?.takeIf { anchor.y - maxLength < it.source.minY }
        below?.toTarget(anchor)?.loadChunk()

        var length = 0
        // A segment only counts when the block its lower end falls in is clear, for a mid block side face anchor that is the row below the piton
        val probe = anchor.clone().subtract(0.0, 1.0, 0.0)
        repeat(maxLength) {
            // Overlap blocks are synced, so the source section's copy is probed for as long as the rope is inside it
            val at = when {
                region?.contains(probe.toVector()) != false -> probe
                else -> below?.toTarget(probe)?.takeIf { below.target.contains(it.toVector()) } ?: return length
            }
            if (at.block.hasCollision) return length
            length++
            probe.subtract(0.0, 1.0, 0.0)
        }
        return length
    }

    /**
     * Suspends while the section below the piton loads, then [take] hands over the rope item, or returns null to back out.
     * [take] is only called once the rope fits and the piton is still there without a rope
     */
    suspend fun attachRope(display: ItemDisplay, owner: UUID, animate: Boolean, take: () -> ItemStack?): RopeAttach {
        val placed = display.placedPiton ?: return RopeAttach.CANCELLED
        // Loading the section below can take a while, and the piton's chunk may have nothing else keeping it loaded until its mirror holds it
        val chunk = display.location.chunkPos
        RopeMirrors.retainChunk(chunk)
        try {
            val length = ropeLength(placed.ropeAnchor(display.world))
            if (!display.isValid || display.hangingRope != null) return RopeAttach.CANCELLED
            if (length < MIN_ROPE_LENGTH) return RopeAttach.NO_SPACE
            val item = take() ?: return RopeAttach.CANCELLED

            display.setHangingRope(HangingRope(owner, item.serializeAsBytes(), length))
            display.restartExpiry()
            PitonParts.spawnRope(display, animate)
            return RopeAttach.ATTACHED
        } finally {
            RopeMirrors.releaseChunk(chunk)
        }
    }

    fun detachRope(display: ItemDisplay): ItemStack? {
        val rope = display.hangingRope ?: return null
        RopeClimb.playersOn(display).forEach(RopeClimb::exit)
        PitonParts.removeRope(display)
        display.setHangingRope(null)
        return rope.toItemStack()
    }
}
