package com.mineinabyss.staminaclimb

import com.mineinabyss.geary.papermc.tracking.items.inventory.GearyPlayerInventory
import com.mineinabyss.geary.papermc.tracking.items.inventory.toGeary
import com.mineinabyss.idofront.nms.aliases.toNMS
import com.mineinabyss.staminaclimb.climbing.ClimbBehaviour
import com.mineinabyss.staminaclimb.component.StaminaModifier
import com.mineinabyss.staminaclimb.component.StaminaModifiers
import com.mineinabyss.staminaclimb.modules.stamina
import com.mineinabyss.staminaclimb.rope.Piton
import com.mineinabyss.staminaclimb.rope.Rope
import com.mineinabyss.staminaclimb.rope.RopeClimb.isRopeClimbing
import com.mineinabyss.staminaclimb.stamina.StaminaBar
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.AABB
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.*

var UUID.isClimbing: Boolean
    get() = ClimbBehaviour.isClimbing[this] ?: false
    set(value) {
        ClimbBehaviour.isClimbing[this] = value
    }

var UUID.canClimb: Boolean
    get() = ClimbBehaviour.canClimb[this] ?: false
    set(value) {
        ClimbBehaviour.canClimb[this] = value
    }

fun Player.stopClimbing() = ClimbBehaviour.stopClimbing(this)

val UUID.climbCooldownDone: Boolean get() = climbCooldown < 0

var UUID.climbCooldown: Long
    get() = ClimbBehaviour.cooldown[this]?.minus(System.currentTimeMillis()) ?: 0
    set(value) {
        ClimbBehaviour.cooldown[this] = value + System.currentTimeMillis()
    }

fun UUID.restartCooldown() {
    ClimbBehaviour.cooldown[this] = System.currentTimeMillis()
}

fun Player.launchInDirection() {
    val direction = location.direction
    velocity = velocity.apply {
        x += direction.x / 20
        z += direction.z / 20
    }
}

const val checkRange = 0.4 //TODO config

/**
 * Get all modifiers from equipment, sorts them based on ModifierOperation Enum value, then folds them into the base value
 * Logic is a mimic of [Modifier Operations](https://minecraft.fandom.com/wiki/Attribute#Operations) in vanilla.
 */
fun GearyPlayerInventory.equipmentModifiers(base: Float, type: StaminaModifier.ModifierType): Float {
    return EquipmentSlot.entries.mapNotNull { slot -> this.get(slot)?.get<StaminaModifiers>()?.modifiers }.flatten()
        .filter { it.type == type }.sortedBy { it.operation }.fold(base) { current, modifier ->
        when (modifier.operation) {
            StaminaModifier.ModifierOperation.ADD -> current + modifier.modifier.toFloat()
            StaminaModifier.ModifierOperation.MULTIPLY_BASE -> current + base * modifier.modifier.toFloat()
            StaminaModifier.ModifierOperation.MULTIPLY -> current * (1 + modifier.modifier).toFloat()
        }
    }
}

val Player.wallDifficulty: Float get() = wallDifficultyAt(location)

//TODO move elsewhere
/** Below 0 means this wall cannot be climbed */
fun Player.wallDifficultyAt(loc: Location): Float {
    if (loc.block.type == Material.WATER) //don't fly if in water
        return -1f

    var totalClimbDifficulty = 0f
    var climbDifficultyCount = 0
    //fix some issues with half slabs by checking half a block higher when the player stands on the ground rather than climbing
    val slabRaise = if (!ClimbBehaviour.isClimbing.containsKey(uniqueId) && toNMS().onGround()) 0.5 else 0.0
    inCube(-1..1 step 2, 0..1, -1..1 step 2) { x, y, z ->
        val to = loc.clone().add(x * checkRange, y + slabRaise, z * checkRange)
        val climbDifficulty = to.block.type.climbDifficulty
        if (climbDifficulty >= 0) {
            totalClimbDifficulty += climbDifficulty
            climbDifficultyCount++
        }
    }
    if (climbDifficultyCount > 0) return (totalClimbDifficulty / climbDifficultyCount)

    if (uniqueId.isClimbing || location.direction.y > 0.5) {
        val climbModifier = loc.clone().add(0.0, 2.2, 0.0).block.type.climbDifficulty
        if (climbModifier > 0) return climbModifier * stamina.config.roofClimbDifficulty
    }
    return -1f
}

val Material.climbDifficulty: Float
    get() = stamina.config.climbDifficulty.getOrDefault(
        this,
        stamina.config.climbDifficultyGeneral.entries.firstOrNull { (name, _) -> name in this.name }?.value
            ?: if (isSolid) 1f else -1f
    )

//TODO move to idofront
inline fun inCube(
    xRange: IntProgression,
    yRange: IntProgression,
    zRange: IntProgression,
    execute: (x: Int, y: Int, z: Int) -> Unit
) {
    for (x in xRange) for (y in yRange) for (z in zRange) execute(x, y, z)
}

var Player.climbEnabled: Boolean
    get() = StaminaBar.climbEnabled(this)
    set(enable) = StaminaBar.setClimbEnabled(this, enable)

val Player.isInClimbableBlock get() = Tag.CLIMBABLE.isTagged(location.block.type)

val Player.isCreative get() = gameMode == GameMode.CREATIVE

/**
 * The server never runs a player's physics, so their velocity is not how fast they fall.
 * This is the movement of their last move packet instead, which the server zeroes on a client tick without one
 */
val Player.fallSpeed: Double
    get() {
        val nms = toNMS()
        return if (nms.onGround()) 0.0 else (-nms.knownMovement.y).coerceAtLeast(0.0)
    }

val Player.hasFooting get() = isRopeClimbing || uniqueId.isClimbing || toNMS().onGround()

/** A rope climber holding sneak to descend would otherwise get the smaller crouching box */
private val Player.standingSize get() = toNMS().getDimensions(Pose.STANDING)
val Player.standingHeight: Double get() = standingSize.height().toDouble()
val Player.standingHalfWidth: Double get() = standingSize.width() / 2.0

fun Player.collidesAt(feet: Vector): Boolean {
    val halfWidth = standingHalfWidth
    // Shrunk like vanilla's own checks, so a box resting flush against a wall does not count as inside it through rounding
    val box = AABB(feet.x - halfWidth, feet.y, feet.z - halfWidth, feet.x + halfWidth, feet.y + standingHeight, feet.z + halfWidth).deflate(1.0E-7)
    return !world.toNMS().noCollision(toNMS(), box)
}

val Player.heldPiton: Piton? get() = inventory.toGeary()?.itemInMainHand?.get<Piton>()
val Player.holdsRope: Boolean get() = inventory.toGeary()?.itemInMainHand?.has<Rope>() == true
val Player.holdsBlock: Boolean get() = inventory.itemInMainHand.type.let { it.isBlock && !it.isAir }

fun Player.findRopeSlot(): Int? = inventory.toGeary()?.let { geary -> (0 until inventory.size).firstOrNull { geary.get(it)?.has<Rope>() == true } }

fun Player.giveOrDrop(item: ItemStack) {
    inventory.addItem(item).values.forEach { world.dropItem(location, it) }
}

val Block.hasCollision get() = collisionShape.boundingBoxes.isNotEmpty()

// Just under a whole block so floating point error in the shape volume still counts
private const val FULL_CUBE_VOLUME = 0.999

val Block.isFullCube: Boolean
    get() = isSolid && collisionShape.boundingBoxes.singleOrNull()?.volume?.let { it >= FULL_CUBE_VOLUME } == true

fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

/** Bukkit's normalize returns NaN for a zero length vector */
fun Vector.normalizeOrZero(): Vector = if (lengthSquared() < 1e-12) zero() else normalize()
