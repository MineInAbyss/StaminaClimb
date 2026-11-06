package com.mineinabyss.staminaclimb.rope

import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.heldPiton
import com.mineinabyss.staminaclimb.holdsBlock
import com.mineinabyss.staminaclimb.rope.RopeClimb.isRopeClimbing
import io.papermc.paper.event.player.PlayerArmSwingEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot

object RopeClimbListener : Listener {
    // The rope is grabbed at hand height, so the feet end up this far below the clicked point
    private const val GRAB_HAND_HEIGHT = 1.0

    @EventHandler(priority = EventPriority.LOWEST)
    fun PlayerInteractEvent.onRightClickWallFromRope() {
        if (action != Action.RIGHT_CLICK_BLOCK || hand != EquipmentSlot.HAND || !player.isRopeClimbing) return
        // Right-clicking a wall with a piton places or throws it instead, and a held block is placed like it is off the rope
        if (player.heldPiton != null || player.holdsBlock) return
        val block = clickedBlock?.takeIf { it.type.isSolid && ClimbBehaviour.isClimbable(it.type) } ?: return
        if (RopeClimb.climbWall(player, block, blockFace)) isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun PlayerInteractAtEntityEvent.onGrabRope() {
        if (hand != EquipmentSlot.HAND) return
        val hitbox = PitonParts.hitboxOf(rightClicked)?.takeIf { it.isRope } ?: return
        isCancelled = true

        val piton = hitbox.piton
        if (RopeClimb.climber(player)?.piton == piton) return
        // A swinging rope is no longer where its hitboxes hang
        if (piton.hangingRope == null || RopeClimb.isSwinging(piton)) return
        // Checked before letting go, so a grab that would fail leaves the player on the rope they hold
        if (!RopeClimb.canGrab(player)) return

        val link = if (hitbox.isMirror) PitonParts.mirrorLinkOf(piton) ?: return else null
        RopeClimb.exit(player)
        val grabY = rightClicked.location.y + clickedPosition.y
        RopeClimb.enter(player, piton, grabY - GRAB_HAND_HEIGHT, link)
    }

    @EventHandler
    fun PlayerInputEvent.onInput() {
        RopeClimb.climber(player)?.updateInput(player, input)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun EntityDismountEvent.onDismount() {
        val climber = RopeClimb.climberOfMount(dismounted) ?: return
        val player = (entity as? Player)?.takeIf { it.uniqueId == climber.player } ?: return
        if (isCancellable) isCancelled = true else RopeClimb.exit(player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun PlayerArmSwingEvent.onSwingOnRope() {
        if (hand != EquipmentSlot.HAND || RopeClimb.swingConsumed(player) || !player.isRopeClimbing) return
        RopeClimb.consumeSwing(player)
        RopeClimb.release(player)
    }

    /** Attacks and right clicks swing the arm after their own packet, so a climber hitting a mob or using an item does not also let go */
    @EventHandler(priority = EventPriority.MONITOR)
    fun PrePlayerAttackEntityEvent.onAttackFromRope() {
        if (player.isRopeClimbing) RopeClimb.consumeSwing(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun PlayerInteractEvent.onRightClickFromRope() {
        if (action.isRightClick && player.isRopeClimbing) RopeClimb.consumeSwing(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun PlayerInteractEntityEvent.onInteractFromRope() {
        if (player.isRopeClimbing) RopeClimb.consumeSwing(player)
    }

    @EventHandler
    fun PlayerQuitEvent.onQuit() {
        RopeClimb.cancelRejoin(player)
        RopeClimb.rememberForRejoin(player)
        RopeClimb.exit(player)
        RopeClimb.forget(player)
    }

    @EventHandler
    fun PlayerJoinEvent.onRejoinRope() = RopeClimb.rejoin(player)

    @EventHandler
    fun PlayerDeathEvent.onDeath() = RopeClimb.exit(entity)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun PlayerTeleportEvent.onTeleport() {
        if (!RopeClimb.isCrossing(player)) RopeClimb.exit(player)
    }

    /** A spectator cannot ride the mount */
    @EventHandler
    fun PlayerGameModeChangeEvent.onGameMode() {
        if (newGameMode == GameMode.SPECTATOR) RopeClimb.exit(player)
    }
}
