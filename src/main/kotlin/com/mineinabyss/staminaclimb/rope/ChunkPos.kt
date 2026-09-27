package com.mineinabyss.staminaclimb.rope

import com.github.shynixn.mccoroutine.bukkit.ticks
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import org.bukkit.Chunk
import org.bukkit.Location
import java.util.*

data class ChunkPos(val world: UUID, val x: Int, val z: Int)

val Location.chunkPos get() = ChunkPos(world.uid, blockX shr 4, blockZ shr 4)
val Chunk.pos get() = ChunkPos(world.uid, x, z)

/** Waits a tick past the load, since Moonrise refuses entities spawned into a chunk that is still finishing loading */
suspend fun Location.loadChunk() {
    if (isChunkLoaded) return
    world.getChunkAtAsync(this).await()
    delay(1.ticks)
}
