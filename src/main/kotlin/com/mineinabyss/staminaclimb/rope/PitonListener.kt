package com.mineinabyss.staminaclimb.rope

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.geary.papermc.datastore.namespacedkey.toComponentKey
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.staminaclimb.giveOrDrop
import com.mineinabyss.staminaclimb.hasFooting
import com.mineinabyss.staminaclimb.heldPiton
import com.mineinabyss.staminaclimb.holdsRope
import com.mineinabyss.staminaclimb.isCreative
import com.mineinabyss.staminaclimb.modules.ropeConfig
import com.mineinabyss.staminaclimb.modules.stamina
import com.mineinabyss.staminaclimb.rope.RopeClimb.isRopeClimbing
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.entity.FishHook
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

object PitonListener : Listener {
    private val messages get() = ropeConfig.messages
    private val sounds get() = ropeConfig.sounds
    private val PLACED_PITON_KEY = "climb:placed_piton".toComponentKey()

    private val Player.canRigPitons get() = hasFooting || isCreative

    /** Runs before the wall climb handler, which cancels every click made while climbing */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun PlayerInteractEvent.onPlacePiton() {
        if (action != Action.RIGHT_CLICK_BLOCK || hand != EquipmentSlot.HAND || useItemInHand() == Event.Result.DENY) return
        val block = clickedBlock ?: return
        if (player.heldPiton?.fusing != false) return
        isCancelled = true
        if (!player.canRigPitons) return

        when (val check = Pitons.checkPlacement(block, blockFace)) {
            is PlacementCheck.Silent -> return
            is PlacementCheck.Error -> return player.error(check.message)
            is PlacementCheck.Ok -> {}
        }
        if (!Pitons.mayPlace(player, block, blockFace)) return

        val item = player.inventory.itemInMainHand
        Pitons.spawn(block, blockFace, item, player.uniqueId, fusing = false, drill = true) ?: return
        if (!player.isCreative) item.subtract()
        player.swingMainHand()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun PlayerInteractAtEntityEvent.onAttachRope() {
        if (hand != EquipmentSlot.HAND) return
        val hitbox = PitonParts.hitboxOf(rightClicked)?.takeUnless { it.isRope } ?: return
        isCancelled = true
        val piton = hitbox.piton
        if (!player.holdsRope || RopeClimb.climber(player)?.piton == piton || !player.canRigPitons) return
        if (piton.hangingRope != null) return player.error(messages.pitonFull)

        stamina.plugin.launch {
            when (Pitons.attachRope(piton, player.uniqueId, animate = true) { player.takeHeldRope() }) {
                RopeAttach.NO_SPACE -> player.error(messages.noSpaceBelow)
                RopeAttach.CANCELLED -> {}
                RopeAttach.ATTACHED -> {
                    player.swingMainHand()
                    sounds.attachRope.play(PitonParts.locationOf(piton, hitbox.isMirror))
                }
            }
        }
    }

    /** The section below may take a moment to load, by then the player can have moved the rope out of their hand */
    private fun Player.takeHeldRope(): ItemStack? {
        if (!isOnline || !holdsRope) return null
        val item = inventory.itemInMainHand
        return item.asOne().also { if (!isCreative) item.subtract() }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun PrePlayerAttackEntityEvent.onAttackParts() {
        val hitbox = PitonParts.hitboxOf(attacked) ?: return
        isCancelled = true
        RopeClimb.consumeSwing(player)

        val piton = hitbox.piton
        // From the ground anyone on the rope can be dropped, from another rope it would pull it out from under them mid swing
        if (player.isRopeClimbing && RopeClimb.countOn(piton) > 0) return

        val rope = piton.hangingRope
        when {
            hitbox.isRope -> if (!RopeClimb.isSwinging(piton)) takeRope(player, piton, hitbox.isMirror)
            piton.placedPiton?.owner == player.uniqueId -> Pitons.breakPiton(piton, hitbox.isMirror)
            rope?.owner == player.uniqueId -> takeRope(player, piton, hitbox.isMirror)
            rope != null -> player.error(messages.ropeNotYours)
            else -> player.error(messages.pitonNotYours)
        }
    }

    private fun takeRope(player: Player, piton: ItemDisplay, viaMirror: Boolean) {
        if (piton.hangingRope?.owner != player.uniqueId) return player.error(messages.ropeNotYours)
        val location = PitonParts.locationOf(piton, viaMirror)
        val rope = Pitons.detachRope(piton) ?: return
        if (!player.isCreative) player.giveOrDrop(rope)
        sounds.detachRope.play(location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun ProjectileLaunchEvent.onLaunchProjectile() {
        val shooter = entity.shooter as? Player ?: return
        // A bobber floating on water never lands and would be traced for as long as the player keeps fishing
        if (entity is FishHook || FusingPitonListener.isThrownPiton(entity)) return
        RopeProjectiles.track(entity, shooter)
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun BlockPlaceEvent.onPlaceInRopePath() {
        if (PitonParts.isRopePath(block)) isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun BlockBreakEvent.onBreakAnchor() = Pitons.dropAllOn(block)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun BlockBurnEvent.onBurnAnchor() = Pitons.dropAllOn(block)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun LeavesDecayEvent.onDecayAnchor() = Pitons.dropAllOn(block)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun BlockFadeEvent.onFadeAnchor() {
        if (!newState.type.isSolid) Pitons.dropAllOn(block)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun EntityChangeBlockEvent.onChangeAnchor() {
        if (!to.isSolid) Pitons.dropAllOn(block)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun BlockExplodeEvent.onExplodeAnchor() = blockList().forEach(Pitons::dropAllOn)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun EntityExplodeEvent.onExplodeAnchor() = blockList().forEach(Pitons::dropAllOn)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun BlockPistonExtendEvent.onPushAnchor() = blocks.forEach(Pitons::dropAllOn)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun BlockPistonRetractEvent.onPullAnchor() = blocks.forEach(Pitons::dropAllOn)

    @EventHandler
    fun EntityAddToWorldEvent.onPitonLoad() {
        val display = (entity as? ItemDisplay)?.takeIf { it.persistentDataContainer.has(PLACED_PITON_KEY) } ?: return
        stamina.plugin.launch {
            // This fires while the chunk is still updating its entities' status, and Moonrise refuses to track entities spawned into it until that is done
            delay(1.ticks)
            if (display.isValid) Pitons.load(display)
        }
    }

    @EventHandler
    fun EntityRemoveFromWorldEvent.onPitonUnload() {
        val display = (entity as? ItemDisplay)?.takeIf { it.placedPiton != null } ?: return
        stamina.plugin.launch {
            // This fires while the chunk is still updating its entities' status, and Moonrise refuses to remove entities from it until that is done
            delay(1.ticks)
            // Loaded back in within that tick, so its parts are still in use
            if (Bukkit.getEntity(display.uniqueId)?.isValid == true) return@launch
            RopeClimb.playersOn(display).forEach(RopeClimb::exit)
            PitonParts.unload(display)
        }
    }
}
