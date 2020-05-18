package com.offz.spigot.staminaclimb.climbing

import com.offz.spigot.staminaclimb.*
import com.offz.spigot.staminaclimb.stamina.StaminaBar
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ClimbBehaviour : Listener {
    val canClimb: MutableMap<UUID, Boolean> = mutableMapOf()
    val isClimbing: MutableMap<UUID, Boolean> = ConcurrentHashMap()
    val cooldown: MutableMap<UUID, Long> = HashMap()

    fun stopClimbing(p: Player) {
        if (p.gameMode == GameMode.SURVIVAL || p.gameMode == GameMode.ADVENTURE) {
            p.allowFlight = false
            p.isFlying = false
        }
        p.flySpeed = 0.1f
        val uuid = p.uniqueId
        isClimbing.remove(uuid)
    }

    @EventHandler
    fun onBlockPlace(e: BlockPlaceEvent) {
        val p = e.player
        val uuid = p.uniqueId
        if (!p.isSneaking && uuid.isClimbing) e.isCancelled = true
        if (cooldown.containsKey(uuid)) uuid.climbCooldown = climbyConfig.WALLJUMP_COOLDOWN
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        val player = e.player
        val uuid = player.uniqueId
        if (cooldown.containsKey(uuid)) uuid.climbCooldown = climbyConfig.WALLJUMP_COOLDOWN
    }

    @EventHandler
    fun onRightClick(e: PlayerInteractEvent) {
        val player = e.player
        val uuid = player.uniqueId
        val velocity = player.velocity

        //if sneaking, don't climb, but do climb if player is also falling
        if (allowClimb(player) && rightClicked(e) && !isClimbing.containsKey(uuid)) {
            val bossBar = StaminaBar.registeredBars[uuid] ?: return
            //remove stamina progress based on how long the player's already fallen
            bossBar.removeProgress(player.fallDistance / 15.0)
            //reduce fall damage by half heart per feather fall level
            val featherFall = player.equipment?.boots
                    ?.getEnchantmentLevel(Enchantment.PROTECTION_FALL)?.times(0.5) ?: 0.0
            val damageAmount = (player.fallDistance - 3) / 1.9 - featherFall
            if (damageAmount >= 1) //prevent player taking damage they can't see, which just makes a sound
                player.damage(damageAmount)

            if (bossBar.progress > 0)
                if (player.wallDifficulty >= 0) {
                    //jump a bit if player is standing on ground and starts climbing
                    if (velocity.y in -0.08..-0.07)
                        player.velocity = player.velocity.add(Vector(0.0, 0.25, 0.0))

                    uuid.isClimbing = true
                    player.allowFlight = true
                    player.isFlying = true
                } else {
                    isClimbing.remove(uuid)
                    uuid.restartCooldown()
                }
            player.flySpeed = 0.03f
        }
    }

    @EventHandler
    fun onLeftClick(e: PlayerAnimationEvent) {
        val player = e.player
        val uuid = player.uniqueId
        //when isClimbing is false, it means player can still do the jump, once it's actually removed from the hashmap, that's when we can't climb
        //don't even ask ok
        if (allowClimb(player) && isClimbing.containsKey(uuid)) {
            //set a cooldown for player not to be able to wall jump right away
            uuid.climbCooldown = climbyConfig.WALLJUMP_COOLDOWN
            val bossBar = StaminaBar.registeredBars[uuid] ?: return

            //find left clicked block (in adventure mode)
            val blocks = player.getLastTwoTargetBlocks(null, 4) //Get two connected blocks player is looking at
            if (blocks.size < 2 || blocks[0].isLiquid || blocks[0].location.distanceSquared(player.location) < 4) return

            //leap
            val blockType = blocks[1].type
            if (leftClicked(blockType)) { //Make sure target block isn't in blacklist
                val direction = player.location.direction
                val x = direction.x
                val y = direction.y
                val z = direction.z

                if (player.wallDifficulty < 0) { //if not at a wall (i.e. double jump)
                    bossBar.removeProgress(0.25) //take away more stamina when in the air
                    player.velocity = player.velocity.apply {
                        this.x = x / 1.8
                        this.y = y / 2 + 0.3
                        this.z = z / 1.8
                    }
                    uuid.climbCooldown = -climbyConfig.AIR_TIME
                } else {
                    bossBar.removeProgress(0.2)
                    player.velocity = player.velocity.apply {
                        this.x = x / 1.8
                        this.y = y / 1
                        this.z = z / 1.8
                    }
                }
            }
        }
    }

    private fun allowClimb(player: Player): Boolean { //does player meet all requirements to be able to climb
        val uuid = player.uniqueId
        //Check if player in survival or adventure mode
        return (player.climbEnabled && uuid.canClimb
                && (!player.isSneaking || player.velocity.y < -0.5) //prevent climb when sneaking, unless already falling
                && uuid.climbCooldownDone
                && (player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE))
        //              && p.getInventory().getItemInMainHand().getType().equals(Material.AIR)) //Make sure player is holding nothing in hand
    }

    private fun rightClicked(e: PlayerInteractEvent): Boolean { //did player do a valid right click
        if (e.clickedBlock == null) return false
        val player = e.player
        val block = e.clickedBlock!!.type
        val heldItem = player.inventory.itemInMainHand.type
        if (heldItem.isBlock && heldItem != Material.AIR) {
            player.uniqueId.climbCooldown = climbyConfig.JUMP_COOLDOWN
            return false
        }
        if (climbyConfig.PREVENT_CLIMB_START.contains(block)) {
            player.uniqueId.climbCooldown = climbyConfig.JUMP_COOLDOWN
            return false
        } else {
            for (interactable in climbyConfig.PREVENT_CLIMB_START_GENERAL) {
                if (block.toString().contains(interactable)) {
                    player.uniqueId.climbCooldown = climbyConfig.JUMP_COOLDOWN
                    return false
                }
            }
        }
        return e.action == Action.RIGHT_CLICK_BLOCK && e.hand == EquipmentSlot.HAND && block.isSolid
    }

    //TODO dont make checks like this separate for left/right click if they do basically the same thing
    private fun leftClicked(block: Material): Boolean { //did player do a valid left click
        //If clicked block is in blacklist, return false
        return !climbyConfig.PREVENT_CLIMB_START.contains(block)
    }

    private fun cooldownComplete(uuid: UUID): Boolean { //is the click cooldown complete
        val playerCooldown = cooldown[uuid] ?: return true
        //If time indicated in cooldown reached, cooldown is complete
        return playerCooldown <= System.currentTimeMillis()
    }
}