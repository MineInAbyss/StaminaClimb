package com.mineinabyss.staminaclimb.config

import com.mineinabyss.idofront.serialization.DurationSerializer
import kotlinx.serialization.Serializable
import org.bukkit.Location
import org.bukkit.SoundCategory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Distances in blocks and speeds in blocks per tick */
@Serializable
class RopeConfig(
    val maxRopeLength: Int = 30,
    val pitonDespawn: @Serializable(DurationSerializer::class) Duration = 10.minutes,
    // Anyone can take a rope down unless its owner is on it or let go of it less than this long ago
    val ropeTakeDelay: @Serializable(DurationSerializer::class) Duration = 10.seconds,
    val climbSpeed: Double = 0.15,
    val pitonFaceOffset: Double = 0.125,
    val pitonHitboxSize: Float = 0.25f,
    val ropeOffset: Double = 0.5,
    // How far behind the rope a climber hangs, so it passes in front of their face rather than through their head
    val holdDistance: Double = 0.35,
    val ropeHitboxWidth: Float = 0.3f,
    // Entities are only clickable near their own position, so the rope hitbox is split into pieces this tall
    val ropeHitboxHeight: Int = 4,
    // Reaching the end of the rope drops you off it when there is floor within this many blocks below
    val groundDismountBuffer: Int = 1,
    // Grabbing a rope or wall while falling slides you down it, losing this much speed per tick and this much stamina per block slid
    val slideDeceleration: Double = 0.03,
    val slideStaminaPerBlock: Float = 0.05f,
    // Regained every tick while hanging still on the rope, climbing at normal speed neither drains nor regains
    val stillStaminaRegenPerTick: Float = 0.003f,
    // Running out of stamina on the rope slides you down it, gaining this much speed per tick
    val exhaustedSlideAcceleration: Double = 0.04,
    // Holding sprint while climbing or descending moves this much faster and costs stamina per block, until the bar is in the red
    val fastClimbMultiplier: Double = 2.5,
    val fastClimbStaminaPerBlock: Float = 0.03f,
    // Pressing jump or sneak twice within this many ticks and holding it also climbs fast, the same window as vanilla's double tap to sprint
    val fastClimbDoubleTapTicks: Int = 7,
    // Item model for the whole rope, custom_model_data flag n shows the block of rope n blocks below the anchor and needs to exist for every length up to maxRopeLength.
    // Unset falls back to a stretched chain block display
    val ropeModel: String? = null,
    val dropAnimationTicksPerSegment: Int = 1,
    val fusingThrowVelocity: Double = 1.2,
    val drill: DrillConfig = DrillConfig(),
    val swing: SwingConfig = SwingConfig(),
    val sounds: RopeSounds = RopeSounds(),
    val messages: RopeMessages = RopeMessages(),
)

// A placed piton starts this far out of the face and screws in over the steps, turning a quarter each step
@Serializable
class DrillConfig(
    val distance: Float = 0.3f,
    val steps: Int = 4,
    val ticksPerStep: Int = 2,
)

@Serializable
class SwingConfig(
    val gravity: Double = 0.08,
    val damping: Double = 0.995,
    val pumpAcceleration: Double = 0.015,
    // A held key only steers the swing plane when it points more than this many degrees across the current motion
    val steerAngle: Double = 30.0,
    // Speed kept per tick while holding jump and sneak together to brake
    val brakeDamping: Double = 0.9,
    // Degrees away from straight down the swing can reach
    val maxAngle: Double = 80.0,
    // Within this many degrees of straight down and this slow, the swing counts as stopped
    val restAngle: Double = 2.0,
    val restSpeed: Double = 0.02,
    // Letting go any slower than this does a wall dash instead of carrying the swing momentum
    val releaseDashBelow: Double = 0.05,
    // Two sprint presses within this many ticks kick off whatever the climber is pressed against, probing this far to each side
    val kickDoubleTapTicks: Int = 6,
    val kickProbe: Double = 0.4,
    val kickStrength: Double = 0.35,
)

@Serializable
class RopeSound(val sound: String, val volume: Float = 1f, val pitch: Float = 1f) {
    fun play(location: Location) = location.world.playSound(location, sound, SoundCategory.PLAYERS, volume, pitch)
}

@Serializable
class RopeSounds(
    val placePiton: RopeSound = RopeSound("block.chain.place", 0.8f, 1.4f),
    val drillPiton: RopeSound = RopeSound("block.chain.hit", 0.6f, 1.6f),
    val breakPiton: RopeSound = RopeSound("block.chain.break", 0.8f, 1.2f),
    val attachRope: RopeSound = RopeSound("item.lead.tied", 1f, 0.9f),
    val detachRope: RopeSound = RopeSound("item.lead.untied", 1f, 0.9f),
    val grabRope: RopeSound = RopeSound("block.wool.step", 1f, 0.8f),
    val climbStep: RopeSound = RopeSound("block.wool.step", 0.6f, 1.1f),
    val throwPiton: RopeSound = RopeSound("entity.snowball.throw", 1f, 0.7f),
    val kickWall: RopeSound = RopeSound("block.stone.step", 1f, 0.7f),
)

@Serializable
class RopeMessages(
    val pitonTopFace: String = "You can only place pitons on the sides or the bottom of a block.",
    val pitonNotSolid: String = "You can only place pitons on solid blocks.",
    val pitonOccupied: String = "You can't place another piton here.",
    val pitonInTransition: String = "You can't place a piton this close to the next layer.",
    val pitonNotYours: String = "You can't break this piton.",
    val pitonFull: String = "This piton can't hold more ropes.",
    val noSpaceBelow: String = "There isn't enough space to climb down from this piton.",
    val ropeInUse: String = "The owner of this rope is still using it.",
    val fusingNoRope: String = "You don't have a rope!",
)
