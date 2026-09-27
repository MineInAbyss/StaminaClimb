package com.mineinabyss.staminaclimb.rope

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.staminaclimb.findRopeSlot
import com.mineinabyss.staminaclimb.giveOrDrop
import com.mineinabyss.staminaclimb.heldPiton
import com.mineinabyss.staminaclimb.isCreative
import com.mineinabyss.staminaclimb.modules.ropeConfig
import com.mineinabyss.staminaclimb.modules.stamina
import io.papermc.paper.datacomponent.DataComponentTypes
import kotlinx.coroutines.delay
import net.kyori.adventure.key.Key
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Snowball
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import java.util.*

object FusingPitonListener : Listener {
    private class Throw(val owner: UUID, val piton: ItemStack, val rope: ItemStack, val free: Boolean) {
        var display: ItemDisplay? = null
        private var chunk: ChunkPos? = null

        /** Nobody needs to be near the other section for the piton to keep flying once it crosses over */
        fun follow(to: ChunkPos) {
            if (to == chunk) return
            release()
            chunk = to
            RopeMirrors.retainChunk(to)
        }

        fun release() {
            chunk?.let(RopeMirrors::releaseChunk)
            chunk = null
        }

        fun dropItem(location: Location, item: ItemStack) {
            if (!free) Pitons.dropItem(location, item)
        }

        fun drop(location: Location) {
            dropItem(location, piton)
            dropItem(location, rope)
        }

        fun refund(location: Location?) {
            if (free) return
            val player = Bukkit.getPlayer(owner) ?: run {
                location?.let(::drop)
                return
            }
            player.giveOrDrop(piton)
            player.giveOrDrop(rope)
        }

        fun ropeUp(existing: ItemDisplay, location: Location) {
            if (existing.hangingRope != null) return drop(location)
            stamina.plugin.launch {
                var attached = false
                // Also runs when the plugin disables mid attach, which would otherwise lose both items
                try {
                    attached = Pitons.attachRope(existing, owner, animate = true) { rope } == RopeAttach.ATTACHED
                } finally {
                    if (attached) dropItem(location, piton) else drop(location)
                }
                if (attached) ropeConfig.sounds.attachRope.play(location)
            }
        }
    }

    private val throws = HashMap<UUID, Throw>()

    // The vanilla air item model renders nothing, leaving the riding display as the only visual
    private val invisibleItem = ItemStack.of(Material.PAPER).apply { setData(DataComponentTypes.ITEM_MODEL, Key.key("minecraft:air")) }

    /** A piton in flight carries its own landing logic, it must not knock other pitons down on the way up */
    fun isThrownPiton(projectile: Entity) = projectile.uniqueId in throws

    private fun finish(projectile: Entity, unloading: Boolean = false) = throws.remove(projectile.uniqueId)?.also { throwData ->
        throwData.release()
        val display = throwData.display ?: return@also
        // Moonrise refuses to remove entities from a chunk while it unloads
        if (!unloading) return@also display.remove()
        stamina.plugin.launch {
            delay(1.ticks)
            display.remove()
        }
    }

    /**
     * Clicks on air arrive already cancelled, so only a denied item use stops the throw.
     * Runs before the wall climb handler, which cancels every click made while climbing
     */
    @EventHandler(priority = EventPriority.LOW)
    fun PlayerInteractEvent.onThrow() {
        if (hand != EquipmentSlot.HAND || (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)) return
        if (useItemInHand() == Event.Result.DENY || player.heldPiton?.fusing != true) return
        isCancelled = true

        val ropeStack = player.findRopeSlot()?.let(player.inventory::getItem) ?: return player.error(ropeConfig.messages.fusingNoRope)
        val pitonStack = player.inventory.itemInMainHand
        val throwData = Throw(player.uniqueId, pitonStack.asOne(), ropeStack.asOne(), free = player.isCreative)

        // Registered before the spawn so the launch event already knows it as a thrown piton
        val snowball = player.launchProjectile(Snowball::class.java, Vector(0.0, ropeConfig.fusingThrowVelocity, 0.0)) {
            it.item = invisibleItem
            it.isPersistent = false
            throws[it.uniqueId] = throwData
        }
        // A cancelled launch event leaves the snowball out of the world
        if (!snowball.isValid) {
            throws.remove(snowball.uniqueId)
            return
        }

        if (!throwData.free) {
            pitonStack.subtract()
            ropeStack.subtract()
        }

        throwData.display = snowball.world.spawn(snowball.location, ItemDisplay::class.java) {
            PitonParts.markPart(it)
            it.setItemStack(throwData.piton)
            it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE
            it.brightness = Display.Brightness(15, 15)
            it.pointAlong(snowball.velocity)
        }.also(snowball::addPassenger)

        steer(snowball, throwData)
        player.swingMainHand()
        ropeConfig.sounds.throwPiton.play(player.location)
    }

    private fun steer(snowball: Snowball, throwData: Throw) = stamina.plugin.launch {
        while (snowball.isValid && throws[snowball.uniqueId] === throwData) {
            throwData.display?.pointAlong(snowball.velocity)
            throwData.follow(snowball.location.chunkPos)
            snowball.crossSections()
            delay(1.ticks)
        }
    }

    /** DeeperWorld only carries living entities across sections, so the piton crosses at the same transition zones by itself */
    private suspend fun Snowball.crossSections() {
        if (!RopeSections.inTransition(location)) return
        RopeSections.linkedLocation(location)?.loadChunk()
        // The snowball flies on while the chunk loads, and changing worlds would replace it with a new entity the throw does not know
        val to = RopeSections.linkedLocation(location)?.takeIf { isValid && it.world == world } ?: return
        val velocity = velocity
        if (teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN)) this.velocity = velocity
    }

    /** The raw piton model has its spike pointing down -Y, near zero [velocity] at the apex keeps the last rotation */
    private fun ItemDisplay.pointAlong(velocity: Vector) {
        if (velocity.lengthSquared() < 1e-6) return
        val dir = velocity.toVector3f().normalize()
        interpolationDelay = 0
        interpolationDuration = 1
        transformation = transformation.run {
            Transformation(translation, Quaternionf().rotationTo(0f, -1f, 0f, dir.x, dir.y, dir.z), scale, rightRotation)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun ProjectileHitEvent.onLand() {
        val throwData = finish(entity) ?: return
        val location = entity.location
        val block = hitBlock ?: return throwData.drop(location)
        val face = hitBlockFace ?: return throwData.drop(location)

        Pitons.find(block, face)?.let { return throwData.ropeUp(it, location) }
        if (Pitons.checkPlacement(block, face) !is PlacementCheck.Ok) return throwData.drop(location)
        if (Bukkit.getPlayer(throwData.owner)?.let { Pitons.mayPlace(it, block, face) } != true) return throwData.drop(location)

        val piton = Pitons.spawn(block, face, throwData.piton, throwData.owner, fusing = true) ?: return throwData.drop(location)
        ropeConfig.sounds.placePiton.play(piton.location)
        stamina.plugin.launch {
            var attached = false
            // A fusing piton never drops, so one left without its rope comes back down with it, also when the plugin disables mid attach
            try {
                attached = Pitons.attachRope(piton, throwData.owner, animate = true) { throwData.rope } == RopeAttach.ATTACHED
            } finally {
                if (!attached) {
                    Pitons.remove(piton, dropRope = false)
                    throwData.drop(piton.location)
                }
            }
        }
    }

    /** Also fires on chunk unload, so an online owner gets the items back rather than having them dropped into an unloading chunk */
    @EventHandler
    fun EntityRemoveFromWorldEvent.onLost() {
        finish(entity, unloading = true)?.refund(entity.location)
    }

    /** Copied and cleared first, since removing a projectile fires [onLost] */
    fun refundAll() {
        val pending = throws.toMap()
        throws.clear()
        pending.forEach { (id, throwData) ->
            val projectile = Bukkit.getEntity(id)
            throwData.release()
            throwData.refund(projectile?.location)
            throwData.display?.remove()
            projectile?.remove()
        }
    }
}
