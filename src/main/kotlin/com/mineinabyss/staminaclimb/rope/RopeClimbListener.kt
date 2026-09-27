package com.mineinabyss.staminaclimb.rope

import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.heldPiton
import com.mineinabyss.staminaclimb.rope.RopeClimb.isRopeClimbing
import io.papermc.paper.event.player.PlayerArmSwingEvent
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
        // Right-clicking a wall with a piton places or throws it instead
        if (player.heldPiton != null) return
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
        val climber = RopeClimb.climber(player) ?: return
        val pressedSprint = input.isSprint && climber.input?.isSprint != true
        climber.input = input
        if (pressedSprint) RopeClimb.sprintPressed(player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun EntityDismountEvent.onDismount() {
        val climber = RopeClimb.climberOfMount(dismounted) ?: return
        val player = (entity as? Player)?.takeIf { it.uniqueId == climber.player } ?: return
        if (isCancellable) isCancelled = true else RopeClimb.exit(player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun PlayerArmSwingEvent.onSwingOnRope() {
        if (hand != EquipmentSlot.HAND || RopeClimb.swingConsumed(player)) return
        val climber = RopeClimb.climber(player) ?: return
        if (climber.input?.isSprint == true) {
            RopeClimb.consumeSwing(player)
            RopeClimb.release(player)
        } else if (RopeClimb.climbWall(player)) RopeClimb.consumeSwing(player)
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
