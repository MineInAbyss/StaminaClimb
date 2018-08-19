package com.offz.minecraft.staminaclimbing.plugin.Climbing;

import com.offz.minecraft.staminaclimbing.plugin.Stamina.StaminaBar;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.*;

public class ClimbBehaviour implements Listener {

    public long JUMP_COOLDOWN = 100; //Milliseconds
    public double SLIDE_DISTANCE = 3; //Blocks
    List BLOCK_BLACKLIST  = Arrays.asList(Material.AIR, Material.TRAP_DOOR, Material.DARK_OAK_DOOR,  Material.ACACIA_DOOR,  Material.BIRCH_DOOR, Material.JUNGLE_DOOR, Material.SPRUCE_DOOR, Material.WOODEN_DOOR, Material.IRON_DOOR, Material.CHEST, Material.ENDER_CHEST, Material.TRAPPED_CHEST);

    public static Map<UUID, Integer> jumpCount = new HashMap<>();
    public static Map<UUID, Boolean> canClimb = new HashMap<>();
    public static Map<UUID, Long> cooldown = new HashMap<>();

    @EventHandler()
    public void onRightClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        BossBar b = StaminaBar.registeredBars.get(uuid);

        if (allowClimb(p) && rightClicked(e)) {
            int playerJumpCount = 0;
            if (jumpCount.containsKey(uuid)) //Remove any chance of null pointer
                playerJumpCount = jumpCount.get(uuid);

            Location L1 = p.getLocation();
            Location L2 = e.getClickedBlock().getLocation();
            L2.add(0.5, -0.5, 0.5);//Get location at center of block
            double distance = L1.distance(L2);

            if (p.isSneaking()) { //Wall slide
                if(p.isOnGround() || distance > SLIDE_DISTANCE) //If player is sneaking on ground, do not perform slide, nor jump
                    return;
                //Calculate slowdown based on current velocity
                Vector velocity = p.getVelocity();
                double y = p.getVelocity().getY();
                double slowdown;
                if (y > -0.85)
                    slowdown = 1.2;
                else if (y > -1.1)
                    slowdown = 1.04;
                else if (y > -1.5)
                    slowdown = 1.015;
                else if (y > -2)
                    slowdown = 1.01;
                else
                    slowdown = 1.01;
                p.setVelocity(velocity.setY(y / slowdown));

                Vector direction = p.getLocation().getDirection();
                y = p.getVelocity().getY(); //Update to account for slowdown

                //Bring player towards the direction they are facing (pull towards wall)
                p.setVelocity((new Vector((velocity.getX() + direction.getX() / 20) * 0.95, y, (velocity.getZ() + direction.getZ() / 20) * 0.95)));

                //Remove stamina based on distance fallen
                double removeStamina = Math.abs(0.02 - y / 20);
                StaminaBar.removeProgress(removeStamina, b);
            } else if (playerJumpCount <= 1 && cooldownComplete(p)) { //Climb jump (right click)
                jumpY(p, e.getBlockFace(), distance, b);
                cooldown.put(uuid, System.currentTimeMillis() + JUMP_COOLDOWN); //Set a cooldown of 2 ticks (100ms)
            }
        }
    }

    @EventHandler()
    public void onLeftClick(PlayerAnimationEvent e) {
        Player p = e.getPlayer();

        if (allowClimb(p) && cooldownComplete(p)) {
            UUID uuid = p.getUniqueId();
            BossBar b = StaminaBar.registeredBars.get(uuid);
            int playerJumpCount = jumpCount.get(uuid);

            //Find left clicked block (in adventure mode)
            List<Block> blocks = p.getLastTwoTargetBlocks(null, 4); //Get two connected blocks player is looking at
            BlockFace blockFace = blocks.get(1).getFace(blocks.get(0)); //Find the face between both of these blocks

            //Horizontal leap
            if (leftClicked(blocks.get(1).getType()) && playerJumpCount <= 2 && playerJumpCount >= 1) { //Make sure target block isn't in blacklist
                Vector direction = p.getLocation().getDirection();
                switch (blockFace) {
                    case UP:
                        if (!p.isOnGround() && playerJumpCount <= 1) {
                            jumpY(p, blockFace, 4, b);
                            return; //This action is the same as jumping (right clicking) on top of block, so it does not proceed further
                        }
                        return;
                    case EAST:
                    case WEST:
                        jumpY(p, blockFace, 4, b);
                        p.setVelocity(p.getVelocity().setZ(Math.signum(direction.getZ()) * 0.5));
                        break;
                    case NORTH:
                    case SOUTH:
                        jumpY(p, blockFace, 4, b);
                        p.setVelocity(p.getVelocity().setX(Math.signum(direction.getX()) * 0.5));
                        break;
                }
                p.setFallDistance(p.getFallDistance() * 0.8f);//Reduce less fall damage than regular jump
                jumpCount.put(uuid, playerJumpCount + 1);//Add extra to jumpCount so player cannot jump after left clicking
//                StaminaBar.removeProgress(0.05, b);//Use slightly more stamina than regular jump
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!StaminaBar.toggled.contains(uuid)) return;//Toggle
        if ((p.getGameMode() == GameMode.ADVENTURE || p.getGameMode() == GameMode.SURVIVAL) && p.isOnGround())//Reset jump count when player hits ground
            jumpCount.put(uuid, 0);
    }

    private void jumpY(Player p, BlockFace blockFace, double distance, BossBar b) {
        Vector velocity = p.getVelocity();
        UUID uuid = p.getUniqueId();
        double x = velocity.getX();
        double y = velocity.getY();
        double z = velocity.getZ();

        Vector direction = p.getLocation().getDirection();
        //While falling, perform slowdown instead of jump
        if (y < -1.5) {
            return;
        } else if (y < -1) {
            p.setVelocity(velocity.setY(y / 1.5));
        }
        else if (y < -0.8) {
            // OLD equation: When velocity at -0.9, velocity becomes -0.25 (same as regular jump), with higher speed, stays closer to its original value
//            p.setVelocity(velocity.setY(y * ((1 / (1.2 * y + 0.28)) + 1)));
            p.setVelocity(velocity.setY(y / 2.5));
        } else if (y < -0.3) {
            p.setVelocity(velocity.setY(0.225));
        } else if (blockFace == BlockFace.UP && distance < 2.4 && !p.isOnGround()) {
            p.setVelocity(velocity.setY(0.37));
        } else if (blockFace != BlockFace.UP) {
            p.setVelocity(velocity.setY(0.37));
        } else
            return;

        //Launch player forward slightly on jump
        p.setVelocity(velocity.setX((x + direction.getX() / 20) * 0.95).setZ((z + direction.getZ() / 20) * 0.95));
        p.setFallDistance(p.getFallDistance() / 1.5f);
        StaminaBar.removeProgress(0.09, b);
        jumpCount.put(uuid, jumpCount.get(uuid) + 1);
    }

    private boolean allowClimb(Player p){
        UUID uuid = p.getUniqueId();
        if(StaminaBar.toggled.contains(uuid) //Check if player has stamina system on
                && canClimb.get(uuid) //Check if player allowed to climb
                && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) //Check if player in survival or adventure mode
                && p.getInventory().getItemInMainHand().getType().equals(Material.AIR)) //Make sure player is holding nothing in hand
            return true;
        return false;
    }

    private boolean rightClicked(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Material block = e.getClickedBlock().getType();

        if (BLOCK_BLACKLIST.contains(block)) {
            cooldown.put(p.getUniqueId(), System.currentTimeMillis() + 300); //Set a cooldown of 2 ticks
            return false;
        } else if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.getHand().equals(EquipmentSlot.HAND) //Check right click, e.getHand makes event only trigger once, otherwise spigot calls it twice
                && block.isSolid()) //Check if clicked block is solid
            return true;
        return false;
    }
    private boolean leftClicked(Material block){
        if(BLOCK_BLACKLIST.contains(block) && block.isBlock()) //If clicked block is in blacklist, return false
            return false;
        return true;
    }
    private boolean cooldownComplete(Player p){
        UUID uuid = p.getUniqueId();

        long playerCooldown = cooldown.get(uuid);
        if (playerCooldown <= System.currentTimeMillis()) //If time indicated in cooldown reached, cooldown is complete
            return true;
        return false;
    }
}