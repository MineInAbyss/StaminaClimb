package com.mineinabyss.staminaclimb.rope

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.staminaclimb.modules.ropeConfig
import com.mineinabyss.staminaclimb.modules.stamina
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import net.kyori.adventure.key.Key
import net.minecraft.core.BlockPos
import org.bukkit.Axis
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.Orientable
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.BoundingBox
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*

/**
 * Only the piton display is persisted, every other part is respawned from it when it loads.
 * A piton reaching into a section overlap gets a mirror, a copy of its parts in the linked section that acts on the source piton
 */
object PitonParts {
    private const val TAG = "climb:rope_part"
    private val PART_OF = NamespacedKey("climb", "part_of")

    /** [piton] is always the source, [isMirror] tells a hit on its copy in the linked section apart */
    class Hitbox(private val source: ItemDisplay, val isRope: Boolean, val isMirror: Boolean) {
        val piton: ItemDisplay get() = source.reloaded()
    }

    private class RopeParts {
        var display: Display? = null
        val hitboxes = mutableListOf<Interaction>()
        var spawning: Job? = null

        /** Without [entities] the parts are only forgotten, for a chunk that discards them itself as it unloads */
        fun remove(entities: Boolean = true) {
            spawning?.cancel()
            spawning = null
            hitboxes.forEach { byHitbox.remove(it.uniqueId) }
            if (entities) {
                display?.remove()
                hitboxes.forEach(Entity::remove)
            }
            display = null
            hitboxes.clear()
        }
    }

    /** [display], [hitbox] and [block] are null when the piton itself lies outside the target section */
    private class MirrorParts(
        val link: SectionLink,
        val chunk: ChunkPos,
        val block: Long?,
        val spot: BlockPos,
        val display: ItemDisplay?,
        val hitbox: Interaction?,
    ) {
        val rope = RopeParts()
    }

    private class Parts(val piton: ItemDisplay, val world: UUID, val block: Long, val spot: BlockPos, val anchor: Location, val hitbox: Interaction) {
        val rope = RopeParts()
        var ropeLength = 0
        var mirror: MirrorParts? = null
        val hiddenFrom = mutableSetOf<UUID>()
        val ropes get() = listOfNotNull(rope, mirror?.rope)
        val spotCenter get() = Location(piton.world, spot.x + 0.5, spot.y + 0.5, spot.z + 0.5)
    }

    private val byPiton = HashMap<UUID, Parts>()
    private val byHitbox = HashMap<UUID, Hitbox>()
    private val byBlock = HashMap<UUID, HashMap<Long, MutableList<ItemDisplay>>>()
    private val byColumn = HashMap<UUID, HashMap<Long, MutableSet<Parts>>>()

    fun hitboxOf(entity: Entity): Hitbox? = byHitbox[entity.uniqueId]
    fun isHitbox(entity: Entity) = entity.uniqueId in byHitbox

    fun pitonsOn(block: Block): List<ItemDisplay> = byBlock[block.world.uid]?.get(BlockPos.asLong(block.x, block.y, block.z))?.toList().orEmpty()
    fun hasPitons(world: World) = byBlock[world.uid]?.isNotEmpty() == true

    fun mirrorLinkOf(piton: ItemDisplay) = byPiton[piton.uniqueId]?.mirror?.link

    fun locationOf(piton: ItemDisplay, onMirror: Boolean): Location =
        byPiton[piton.uniqueId]?.mirror?.takeIf { onMirror }?.link?.toTarget(piton.location) ?: piton.location

    fun displaysOf(piton: ItemDisplay): List<ItemDisplay> = listOfNotNull(piton, byPiton[piton.uniqueId]?.mirror?.display)

    fun hasMirrorIn(piton: UUID, chunk: ChunkPos) = byPiton[piton]?.mirror?.chunk == chunk

    /** The spot a piton or its mirror sits in, or a block its rope hangs through, a bottom face rope blocks one extra row below its end */
    fun isRopePath(block: Block): Boolean = byColumn[block.world.uid]?.get(columnOf(block.x, block.z)).orEmpty().any { parts ->
        listOfNotNull(parts.spot, parts.mirror?.spot).any { it.x == block.x && it.z == block.z && block.y in it.y - parts.ropeLength..it.y }
    }

    private fun columnOf(x: Int, z: Int) = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    private fun indexColumn(world: UUID, spot: BlockPos, parts: Parts) {
        byColumn.getOrPut(world, ::HashMap).getOrPut(columnOf(spot.x, spot.z), ::mutableSetOf) += parts
    }

    private fun unindexColumn(world: UUID, spot: BlockPos, parts: Parts) {
        val columns = byColumn[world] ?: return
        val column = columnOf(spot.x, spot.z)
        val entries = columns[column] ?: return
        entries -= parts
        if (entries.isEmpty()) columns.remove(column)
        if (columns.isEmpty()) byColumn.remove(world)
    }

    /** A newly placed piton passes [loadMirror] so the linked section's chunk loads and gets indexed, a loading one waits for that chunk to load on its own */
    fun ensure(piton: ItemDisplay, loadMirror: Boolean = false) {
        val placed = piton.placedPiton ?: return
        piton.brightness = Display.Brightness(15, 15)
        // Zero size turns off client side culling for the piton
        piton.displayWidth = 0f
        piton.displayHeight = 0f

        // A piton whose chunk reloads before its unload is handled comes back as a new entity, the old parts still point at the dead one
        byPiton[piton.uniqueId]?.takeIf { it.piton !== piton }?.let { forget(it.piton, unloading = true) }
        val parts = byPiton.getOrPut(piton.uniqueId) {
            val size = ropeConfig.pitonHitboxSize
            val location = placed.faceCenter(piton.world).add(placed.face.direction.multiply(ropeConfig.pitonFaceOffset)).subtract(0.0, size / 2.0, 0.0)
            val hitbox = spawnPitonHitbox(location, piton, isMirror = false)
            val block = BlockPos(placed.blockX, placed.blockY, placed.blockZ)
            register(piton.world.uid, block.asLong(), piton)
            val face = placed.face
            Parts(piton, piton.world.uid, block.asLong(), block.offset(face.modX, face.modY, face.modZ), placed.ropeAnchor(piton.world), hitbox)
                .also { indexColumn(it.world, it.spot, it) }
        }

        if (parts.ropeLength == 0) parts.ropeLength = piton.hangingRope?.length ?: 0
        refresh(parts, loadMirror, animate = false)
    }

    fun spawnRope(piton: ItemDisplay, animate: Boolean) {
        val rope = piton.hangingRope ?: return
        val parts = byPiton[piton.uniqueId] ?: return
        parts.ropeLength = rope.length
        refresh(parts, loadMirror = true, animate)
    }

    private fun refresh(parts: Parts, loadMirror: Boolean, animate: Boolean) {
        syncMirror(parts, loadMirror)
        if (parts.ropeLength > 0) {
            if (parts.rope.display == null) spawnRope(parts, parts.rope, parts.anchor, RopeSections.regionAt(parts.anchor), isMirror = false, animate)
            parts.mirror?.let { mirror ->
                if (mirror.rope.display == null) spawnRope(parts, mirror.rope, mirror.link.toTarget(parts.anchor), mirror.link.target, isMirror = true, animate)
            }
        }
        sweepOrphans(parts)
    }

    /** Mirrors into the section the spot overlaps with, or the one the lowest bit of rope still inside the piton's own section overlaps with */
    private fun mirrorLink(parts: Parts): SectionLink? {
        RopeSections.linkAt(parts.spotCenter)?.let { return it }
        if (parts.ropeLength == 0) return null
        val anchor = parts.anchor
        val region = RopeSections.regionAt(anchor) ?: return null
        return RopeSections.linkAt(anchor.clone().apply { y = maxOf(y - parts.ropeLength + 0.5, region.minY) })
    }

    private fun syncMirror(parts: Parts, load: Boolean) {
        val link = mirrorLink(parts)
        if (link == parts.mirror?.link) return
        removeMirror(parts, entities = true, keepIndex = false)
        if (link == null) return
        val at = link.toTarget(parts.piton.location)
        if (at.isChunkLoaded) parts.mirror = spawnMirror(parts, link, at)
        else if (load) stamina.plugin.launch {
            at.loadChunk()
            if (byPiton[parts.piton.uniqueId] === parts) refresh(parts, loadMirror = false, animate = false)
        }
    }

    /** [at] is the mirrored piton location, every mirror part shares its block column and so its chunk */
    private fun spawnMirror(parts: Parts, link: SectionLink, at: Location): MirrorParts {
        val piton = parts.piton
        val display = if (link.target.contains(at.toVector())) at.world.spawn(at, ItemDisplay::class.java) {
            markPart(it, piton)
            it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
            it.setItemStack(piton.itemStack)
            it.transformation = piton.transformation
            it.brightness = Display.Brightness(15, 15)
            it.displayWidth = 0f
            it.displayHeight = 0f
        } else null

        val hitbox = display?.let { spawnPitonHitbox(link.toTarget(parts.hitbox.location), piton, isMirror = true) }
        val delta = link.delta
        val block = display?.let { BlockPos.of(parts.block).offset(delta.blockX, delta.blockY, delta.blockZ).asLong() }
        block?.let { register(link.world.uid, it, piton) }

        val spot = parts.spot.offset(delta.blockX, delta.blockY, delta.blockZ)
        indexColumn(link.world.uid, spot, parts)
        RopeMirrors.record(at.chunk, piton)
        return MirrorParts(link, at.chunkPos, block, spot, display, hitbox)
    }

    private fun removeMirror(parts: Parts, entities: Boolean, keepIndex: Boolean) {
        val mirror = parts.mirror ?: return
        parts.mirror = null
        mirror.rope.remove(entities)
        mirror.hitbox?.let { byHitbox.remove(it.uniqueId) }
        if (entities) {
            mirror.display?.remove()
            mirror.hitbox?.remove()
            sweepOrphans(parts, mirror.link.world to mirror.spot)
        }
        mirror.block?.let { unregister(mirror.link.world.uid, it, parts.piton) }
        unindexColumn(mirror.link.world.uid, mirror.spot, parts)
        if (!keepIndex) RopeMirrors.drop(mirror.chunk, parts.piton.uniqueId)
    }

    /** Runs from the unload event of [chunk], which discards the non persistent mirror parts itself and where Moonrise refuses to remove them */
    fun forgetMirrorsIn(chunk: ChunkPos) = byPiton.values.filter { it.mirror?.chunk == chunk }.forEach {
        removeMirror(it, entities = false, keepIndex = true)
    }

    private fun spawnPitonHitbox(location: Location, piton: ItemDisplay, isMirror: Boolean): Interaction {
        val size = ropeConfig.pitonHitboxSize
        return location.world.spawn(location, Interaction::class.java) {
            markPart(it, piton)
            it.interactionWidth = size
            it.interactionHeight = size
            it.isResponsive = false
        }.also { byHitbox[it.uniqueId] = Hitbox(piton, isRope = false, isMirror) }
    }

    private fun spawnRope(parts: Parts, rope: RopeParts, anchor: Location, region: BoundingBox?, isMirror: Boolean, animate: Boolean) {
        val length = parts.ropeLength
        val shown = List(length) { region?.contains(anchor.clone().subtract(0.0, it + 0.5, 0.0).toVector()) ?: true }
        val first = shown.indexOf(true).takeIf { it >= 0 } ?: return
        val last = shown.lastIndexOf(true)

        val bottom = anchor.clone().subtract(0.0, length.toDouble(), 0.0)
        val hitboxBottom = anchor.clone().subtract(0.0, last + 1.0, 0.0)
        val hitboxHeight = last - first + 1

        val unrollTicks = (ropeConfig.dropAnimationTicksPerSegment * length).takeIf { animate && it > 0 }
        val display = spawnRopeDisplay(parts.piton, bottom, shown, extent = if (unrollTicks == null) 1f else 0f)
        rope.display = display
        if (unrollTicks == null) return spawnRopeHitboxes(parts, rope, hitboxBottom, hitboxHeight, isMirror)

        rope.spawning = stamina.plugin.launch {
            // The client only interpolates from a state it has already ticked, and after a single tick the spawn and the update can still land in the same client tick
            delay(2.ticks)
            display.interpolationDelay = 0
            display.interpolationDuration = unrollTicks
            display.transformation = display.ropeTransformation(length, 1f)
            delay(unrollTicks.ticks)
            spawnRopeHitboxes(parts, rope, hitboxBottom, hitboxHeight, isMirror)
            rope.spawning = null
        }
    }

    private fun spawnRopeHitboxes(parts: Parts, rope: RopeParts, bottom: Location, length: Int, isMirror: Boolean) {
        val pieceHeight = ropeConfig.ropeHitboxHeight.coerceAtLeast(1)
        val hiddenFrom = parts.hiddenFrom.mapNotNull(Bukkit::getPlayer)
        var pieceBottom = bottom.clone()
        var remaining = length

        while (remaining > 0) {
            val height = minOf(pieceHeight, remaining)
            val hitbox = bottom.world.spawn(pieceBottom, Interaction::class.java) {
                markPart(it, parts.piton)
                it.interactionWidth = ropeConfig.ropeHitboxWidth
                it.interactionHeight = height.toFloat()
                it.isResponsive = false
            }

            byHitbox[hitbox.uniqueId] = Hitbox(parts.piton, isRope = true, isMirror)
            rope.hitboxes += hitbox
            hiddenFrom.forEach { it.hideEntity(stamina.plugin, hitbox) }
            pieceBottom = pieceBottom.add(0.0, height.toDouble(), 0.0)
            remaining -= height
        }
    }

    /** A climber's eyes sit inside their own rope's hitbox, which would take every click meant for the item in hand */
    fun hideRopeFrom(player: Player, piton: ItemDisplay) {
        val parts = byPiton[piton.uniqueId] ?: return
        if (!parts.hiddenFrom.add(player.uniqueId)) return
        parts.ropes.forEach { rope -> rope.hitboxes.forEach { player.hideEntity(stamina.plugin, it) } }
    }

    fun showRopeTo(player: UUID, piton: ItemDisplay) {
        val parts = byPiton[piton.uniqueId] ?: return
        if (!parts.hiddenFrom.remove(player)) return
        val online = Bukkit.getPlayer(player) ?: return
        parts.ropes.forEach { rope -> rope.hitboxes.forEach { online.showEntity(stamina.plugin, it) } }
    }

    /** The hitboxes stay where the rope hangs at rest */
    fun setRopeSwing(piton: ItemDisplay, dir: Vector?) {
        val parts = byPiton[piton.uniqueId] ?: return
        val rotation = dir?.let { Quaternionf().rotationTo(0f, -1f, 0f, it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) } ?: Quaternionf()
        parts.ropes.forEach { rope ->
            val display = rope.display ?: return@forEach
            display.interpolationDelay = 0
            display.interpolationDuration = 1
            display.transformation = display.ropeTransformation(parts.ropeLength, 1f, rotation)
        }
    }

    /**
     * The display sits at the bottom of the rope, since a display's culling box only reaches up from its position.
     * The box is as wide as the rope is long so a swing never leaves it.
     * [shown] holds one entry per block of rope from the anchor down, the block fallback cannot hide any of them
     */
    private fun spawnRopeDisplay(piton: ItemDisplay, bottom: Location, shown: List<Boolean>, extent: Float): Display {
        val model = ropeConfig.ropeModel
        val length = shown.size
        val configure = { display: Display ->
            markPart(display, piton)
            display.brightness = Display.Brightness(15, 15)
            display.displayWidth = 2f * length
            display.displayHeight = length.toFloat()
            display.transformation = display.ropeTransformation(length, extent)
        }

        return if (model == null) bottom.world.spawn(bottom, BlockDisplay::class.java) {
            it.block = Material.IRON_CHAIN.createBlockData().also { data -> (data as Orientable).axis = Axis.Y }
            configure(it)
        } else bottom.world.spawn(bottom, ItemDisplay::class.java) {
            it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
            it.setItemStack(ropeItem(model, shown))
            configure(it)
        }
    }

    private fun ropeItem(model: String, shown: List<Boolean>) = ItemStack.of(Material.PAPER).apply {
        setData(DataComponentTypes.ITEM_MODEL, Key.key(model))
        setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addFlags(shown))
    }

    /**
     * Pivots the rope around its anchor, [length] blocks above the display.
     * The item model hangs down from its own origin, while the block fallback is one stretched block hanging off its corner.
     * [extent] squashes the rope up toward the anchor for the drop animation
     */
    private fun Display.ropeTransformation(length: Int, extent: Float, rotation: Quaternionf = Quaternionf()): Transformation {
        val anchor = Vector3f(0f, length.toFloat(), 0f)
        if (this is ItemDisplay) return Transformation(anchor, Quaternionf(rotation), Vector3f(1f, extent, 1f), Quaternionf())
        val drop = length * extent
        val translation = rotation.transform(Vector3f(-0.5f, -drop, -0.5f)).add(anchor)
        return Transformation(translation, Quaternionf(rotation), Vector3f(1f, drop, 1f), Quaternionf())
    }

    fun removeRope(piton: ItemDisplay) {
        val parts = byPiton[piton.uniqueId] ?: return
        parts.ropes.forEach { it.remove() }
        parts.ropeLength = 0
        syncMirror(parts, load = false)
        sweepOrphans(parts)
    }

    fun remove(piton: ItemDisplay) = forget(piton, unloading = false)

    fun unload(piton: ItemDisplay) = forget(piton, unloading = true)

    private fun forget(piton: ItemDisplay, unloading: Boolean) {
        val parts = byPiton[piton.uniqueId]?.takeIf { it.piton === piton } ?: return
        byPiton.remove(piton.uniqueId)
        parts.rope.remove()
        removeMirror(parts, entities = true, keepIndex = unloading)
        byHitbox.remove(parts.hitbox.uniqueId)
        parts.hitbox.remove()
        unregister(parts.world, parts.block, piton)
        unindexColumn(parts.world, parts.spot, parts)
    }

    private fun register(world: UUID, block: Long, piton: ItemDisplay) {
        byBlock.getOrPut(world, ::HashMap).getOrPut(block, ::mutableListOf) += piton
    }

    private fun unregister(world: UUID, block: Long, piton: ItemDisplay) {
        val blocks = byBlock[world] ?: return
        val onBlock = blocks[block] ?: return
        onBlock.removeIf { it.uniqueId == piton.uniqueId }
        if (onBlock.isEmpty()) blocks.remove(block)
        if (blocks.isEmpty()) byBlock.remove(world)
    }

    /** Also clears parts left behind in loaded chunks by a previous plugin instance, such as after a reload */
    fun restoreLoaded() {
        // Leftovers are cleared in every world first, a piton loading in one world can mirror into another
        val entities = Bukkit.getWorlds().flatMap { it.entities }
        entities.filter { TAG in it.scoreboardTags }.forEach(Entity::remove)
        entities.filterIsInstance<ItemDisplay>().filter { it.isValid }.forEach(Pitons::load)
        RopeMirrors.trackLoaded()
    }

    /** Works off the registry rather than the piton displays, whose entities may already be gone by shutdown */
    fun clear() {
        byPiton.values.forEach { parts ->
            parts.ropes.forEach { it.remove() }
            parts.hitbox.remove()
            parts.mirror?.display?.remove()
            parts.mirror?.hitbox?.remove()
        }
        byPiton.clear()
        byHitbox.clear()
        byBlock.clear()
        byColumn.clear()
        RopeMirrors.clear()
    }

    /** [piton] ties the part to the piton it belongs to, so one left behind untracked can be found and removed */
    fun markPart(entity: Entity, piton: ItemDisplay? = null) {
        entity.isPersistent = false
        entity.addScoreboardTag(TAG)
        piton?.let { entity.persistentDataContainer.set(PART_OF, PersistentDataType.STRING, it.uniqueId.toString()) }
    }

    /**
     * A chunk unloading and coming back before its parts are discarded can leave copies the registry no longer knows.
     * They still show but no longer respond to anything, so every column the piton's parts can sit in is cleared of them.
     * [extra] adds a column the parts have just left, such as a removed mirror's
     */
    private fun sweepOrphans(parts: Parts, extra: Pair<World, BlockPos>? = null) {
        val pitonId = parts.piton.uniqueId.toString()
        val tracked = (listOfNotNull(parts.hitbox, parts.mirror?.display, parts.mirror?.hitbox) + parts.ropes.flatMap { listOfNotNull(it.display) + it.hitboxes })
            .mapTo(HashSet()) { it.uniqueId }
        val columns = listOfNotNull(
            parts.piton.world to parts.spot,
            parts.mirror?.let { it.link.world to it.spot },
            extra,
        )
        val reach = ropeConfig.maxRopeLength + 2.0
        columns.forEach { (world, spot) ->
            val column = BoundingBox(spot.x.toDouble(), spot.y - reach, spot.z.toDouble(), spot.x + 1.0, spot.y + 2.0, spot.z + 1.0)
            world.getNearbyEntities(column) { it.uniqueId !in tracked && it.persistentDataContainer.get(PART_OF, PersistentDataType.STRING) == pitonId }
                .forEach(Entity::remove)
        }
    }
}
