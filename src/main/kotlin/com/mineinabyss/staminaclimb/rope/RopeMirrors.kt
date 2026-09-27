package com.mineinabyss.staminaclimb.rope

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.staminaclimb.modules.stamina
import com.mineinabyss.staminaclimb.toUuidOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.NamespacedKey
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import java.util.*

/**
 * A mirror lives in another section than its source piton, whose chunks load independently.
 * Every chunk holding mirror parts indexes their sources, and holds a plugin ticket on each source chunk while it is loaded itself so the mirror always has its source to act on
 */
object RopeMirrors : Listener {
    private val INDEX = NamespacedKey("climb", "rope_mirrors")
    private val PITON = NamespacedKey("climb", "piton")
    private val WORLD = NamespacedKey("climb", "world")
    private val POSITION = NamespacedKey("climb", "position")
    private val ENTRIES = PersistentDataType.LIST.dataContainers()

    // A source chunk whose entities take longer than this to load is checked again the next time the mirror chunk loads
    const val ENTITY_WAIT_TICKS = 100

    private class Source(val piton: UUID, val world: UUID, val x: Int, val y: Int, val z: Int) {
        val chunk get() = ChunkPos(world, x shr 4, z shr 4)
    }

    private val held = HashMap<ChunkPos, HashMap<UUID, ChunkPos>>()

    // Bukkit keeps a single plugin ticket per chunk however often it is added, so the mirror chunks holding each source chunk are counted here
    private val tickets = HashMap<ChunkPos, Int>()
    private val ticketed = HashSet<ChunkPos>()

    fun record(chunk: Chunk, piton: ItemDisplay) {
        val location = piton.location
        val sources = chunk.sources
        if (sources.none { it.piton == piton.uniqueId }) {
            chunk.sources = sources + Source(piton.uniqueId, piton.world.uid, location.blockX, location.blockY, location.blockZ)
        }
        hold(chunk.pos, piton.uniqueId, location.chunkPos)
    }

    fun drop(chunk: ChunkPos, piton: UUID) {
        release(chunk, piton)
        val world = Bukkit.getWorld(chunk.world)?.takeIf { it.isChunkLoaded(chunk.x, chunk.z) } ?: return
        val loaded = world.getChunkAt(chunk.x, chunk.z)
        loaded.sources = loaded.sources.filterNot { it.piton == piton }
    }

    @EventHandler
    fun ChunkLoadEvent.onMirrorChunkLoad() = track(chunk)

    @EventHandler
    fun ChunkUnloadEvent.onMirrorChunkUnload() {
        val pos = chunk.pos
        val sources = held.remove(pos) ?: return
        PitonParts.forgetMirrorsIn(pos)
        sources.values.forEach(::releaseChunk)
    }

    fun trackLoaded() = Bukkit.getWorlds().forEach { world -> world.loadedChunks.forEach(::track) }

    private fun track(chunk: Chunk) {
        val sources = chunk.sources.takeIf { it.isNotEmpty() } ?: return
        val pos = chunk.pos
        sources.forEach { source ->
            hold(pos, source.piton, source.chunk)
            stamina.plugin.launch {
                // Spawning into a chunk from its own load event trips Moonrise, as with PitonListener.onPitonLoad
                delay(1.ticks)
                verify(chunk, source)
            }
        }
    }

    private suspend fun verify(chunk: Chunk, source: Source) {
        val sourceChunk = Bukkit.getWorld(source.world)?.getChunkAtAsync(source.chunk.x, source.chunk.z)?.await()
        var waited = 0
        while (sourceChunk?.isEntitiesLoaded == false && chunk.isLoaded && waited++ < ENTITY_WAIT_TICKS) delay(1.ticks)
        if (!chunk.isLoaded || sourceChunk?.isEntitiesLoaded == false) return
        // The piton loads a tick after its entity is added, the same wait as PitonListener.onPitonLoad
        delay(1.ticks)
        if (!chunk.isLoaded) return
        (Bukkit.getEntity(source.piton) as? ItemDisplay)?.let { PitonParts.ensure(it) }
        if (!PitonParts.hasMirrorIn(source.piton, chunk.pos)) drop(chunk.pos, source.piton)
    }

    private fun hold(chunk: ChunkPos, piton: UUID, source: ChunkPos) {
        // A chunk holding a ticket on itself, or on a chunk holding one on it, would never unload
        if (chunk == source || held[source]?.containsValue(chunk) == true) return
        if (held.getOrPut(chunk, ::HashMap).put(piton, source) == null) retainChunk(source)
    }

    /** A plugin ticket keeps the chunk's entities ticking, which a chunk only loaded for a lookup does not */
    fun retainChunk(chunk: ChunkPos) {
        val count = (tickets[chunk] ?: 0) + 1
        tickets[chunk] = count
        if (count == 1) acquire(chunk)
    }


    private fun release(chunk: ChunkPos, piton: UUID) {
        val sources = held[chunk] ?: return
        val source = sources.remove(piton) ?: return
        if (sources.isEmpty()) held.remove(chunk)
        releaseChunk(source)
    }

    /** Adding a plugin ticket loads its chunk on the spot, so the chunk is loaded asynchronously first */
    private fun acquire(source: ChunkPos) {
        val world = Bukkit.getWorld(source.world) ?: return
        world.getChunkAtAsync(source.x, source.z).thenRun {
            if (source in tickets && ticketed.add(source)) world.addPluginChunkTicket(source.x, source.z, stamina.plugin)
        }
    }

    fun releaseChunk(source: ChunkPos) {
        val count = tickets[source] ?: return
        if (count > 1) {
            tickets[source] = count - 1
            return
        }
        tickets.remove(source)
        if (ticketed.remove(source)) Bukkit.getWorld(source.world)?.removePluginChunkTicket(source.x, source.z, stamina.plugin)
    }

    fun clear() {
        ticketed.forEach { Bukkit.getWorld(it.world)?.removePluginChunkTicket(it.x, it.z, stamina.plugin) }
        ticketed.clear()
        tickets.clear()
        held.clear()
    }

    private var Chunk.sources: List<Source>
        get() = persistentDataContainer.get(INDEX, ENTRIES)?.mapNotNull { it.toSource() }.orEmpty()
        set(value) {
            val data = persistentDataContainer
            if (value.isEmpty()) data.remove(INDEX)
            else data.set(INDEX, ENTRIES, value.map { it.toContainer(data.adapterContext) })
        }

    private fun Source.toContainer(context: PersistentDataAdapterContext) = context.newPersistentDataContainer().apply {
        set(PITON, PersistentDataType.STRING, piton.toString())
        set(WORLD, PersistentDataType.STRING, world.toString())
        set(POSITION, PersistentDataType.INTEGER_ARRAY, intArrayOf(x, y, z))
    }

    private fun PersistentDataContainer.toSource(): Source? {
        val piton = get(PITON, PersistentDataType.STRING)?.toUuidOrNull() ?: return null
        val world = get(WORLD, PersistentDataType.STRING)?.toUuidOrNull() ?: return null
        val (x, y, z) = get(POSITION, PersistentDataType.INTEGER_ARRAY)?.takeIf { it.size == 3 } ?: return null
        return Source(piton, world, x, y, z)
    }

}
