package com.offz.spigot.staminaclimb.Climbing;

import com.offz.spigot.staminaclimb.Stamina.StaminaBar;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClimbBehaviour implements Listener {
    public static Map<UUID, Boolean> canClimb = new HashMap<>();
    public static Map<UUID, Boolean> isClimbing = new ConcurrentHashMap<>();
    public static Map<UUID, Long> cooldown = new HashMap<>();
    private static List<String> BLOCK_BLACKLIST_GENERALIZED = Arrays.asList(
            "DOOR",
            "FENCE",
            "FURNACE",
            "BED",
            "BEE",
            "BELL",
            "BOAT",
            "CHEST",
            "MINECART",
            "BUTTON",
            "SIGN",
            "SHULKER_BOX");
    private List BLOCK_BLACKLIST = Arrays.asList(
            Material.ANVIL,
            Material.BEACON,
            Material.BARREL,
            Material.CAMPFIRE,
            Material.CARTOGRAPHY_TABLE,
            Material.CAULDRON,
            Material.COMMAND_BLOCK,
            Material.COMPOSTER,
            Material.CRAFTING_TABLE,
            Material.BREWING_STAND,
            Material.DAYLIGHT_DETECTOR,
            Material.DISPENSER,
            Material.DROPPER,
            Material.ENCHANTING_TABLE,
            Material.ENDER_CHEST,
            Material.FARMLAND,
//            Material.FURNACE, //moving to generalized list to cover the blast furnace
            Material.GRINDSTONE,
            Material.HOPPER,
            Material.HOPPER_MINECART,
            Material.ITEM_FRAME,
            Material.LANTERN,
            Material.LECTERN,
            Material.LEVER,
            Material.LOOM,
            Material.NOTE_BLOCK,
            Material.SCAFFOLDING,
            Material.SMOKER,
//            Material.SMITHING_TABLE, //There is currently no reason to right-click this block, so I'm commenting it out.
            Material.STONECUTTER,
            Material.SWEET_BERRY_BUSH);
    private long JUMP_COOLDOWN = 300; //Milliseconds
    private long WALL_JUMP_COOLDOWN = 300;

    public static void stopClimbing(Player p) {
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setFlySpeed(0.1f);

        UUID uuid = p.getUniqueId();
        ClimbBehaviour.isClimbing.remove(uuid);
    }

    public static boolean atWall(Location loc, UUID uuid) { //checks if player is at climbable wall
        if (loc.getBlock().getType().equals(Material.WATER)) //don't fly if in water
            return false;
        for (int x = -1; x <= 1; x += 2) { //check for block to hang onto in a 2x2x2 area around player
            for (int y = 0; y <= 1; y += 1) {
                for (int z = -1; z <= 1; z += 2) {
                    double checkRange = 0.4;
                    Location to = new Location(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ()).add(x * checkRange, y, z * checkRange);

                    //fix some issues with half slabs by checking half a block higher when the player isn't yet climbing
                    if (!isClimbing.containsKey(uuid)) {
                        to.add(0, 0.5, 0);

                    }
                    if (to.getBlock().getType().isSolid())
                        return true;
                }
            }
        }
        return false;
    }

    @EventHandler()
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        Location loc = p.getLocation();
        if (!p.isSneaking() && isClimbing.containsKey(uuid) && isClimbing.get(uuid)) {
            if (e.getBlock().getLocation().distance(loc) > 2.5) {
                stopClimbing(p);
                p.setVelocity(p.getVelocity().setY(0));
                return;
            }
            e.setCancelled(true);
        }
        if (cooldown.containsKey(uuid)) {
            cooldown.put(uuid, System.currentTimeMillis() + WALL_JUMP_COOLDOWN);
        }
    }

    @EventHandler()
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (cooldown.containsKey(uuid)) {
            cooldown.put(uuid, System.currentTimeMillis() + WALL_JUMP_COOLDOWN);
        }
    }

    @EventHandler()
    public void onRightClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        Location L1 = p.getLocation();

        Vector v = p.getVelocity();

        //todo make pick improve stamina and speed?

        if (allowClimb(p) && rightClicked(e) && cooldownComplete(uuid) && (!p.isSneaking() || v.getY() < -0.5) && !isClimbing.containsKey(uuid) && p.getInventory().getItemInMainHand().getType().equals(Material.IRON_PICKAXE) && p.getInventory().getItemInMainHand().getItemMeta().getDisplayName().equals(ChatColor.LIGHT_PURPLE + "Climbing Axe")) {
            StaminaBar.removeProgress(p.getFallDistance() / 15, uuid);
            double featherFall = 0;
            if (p.getEquipment() != null && p.getEquipment().getBoots() != null)
                featherFall = p.getEquipment().getBoots().getEnchantmentLevel(Enchantment.PROTECTION_FALL) * 0.5; //reduce fall damage by half heart per feather fall level
            double damangeAmount = ((p.getFallDistance() - 3) / 1.9) - featherFall;
            if (damangeAmount >= 1) //prevent player taking damage they can't see, which just makes a sound
                p.damage(damangeAmount);

            //jump a bit if player is standing on ground and starts climbing
            if (v.getY() > -0.08 && v.getY() < -0.07 && atWall(L1, uuid)) {
                p.setVelocity(v.add(new Vector(0, 0.25, 0)));
            }

            if (StaminaBar.registeredBars.get(uuid).getProgress() > 0) {
                if (atWall(L1, uuid)) {
                    isClimbing.put(uuid, true);
                    p.setAllowFlight(true);
                    p.setFlying(true);
                } else {
                    isClimbing.put(uuid, false);
                    cooldown.put(uuid, System.currentTimeMillis());
                }
            }
            p.setFlySpeed(0.0391f);
        }

        //if sneaking, don't climb, but do climb if player is also falling
        if (allowClimb(p) && rightClicked(e) && cooldownComplete(uuid) && (!p.isSneaking() || v.getY() < -0.5) && !isClimbing.containsKey(uuid)){
            //            //remove stamina progress based on how long the player's already fallen
            StaminaBar.removeProgress(p.getFallDistance() / 15, uuid);
            double featherFall = 0;
            if (p.getEquipment() != null && p.getEquipment().getBoots() != null)
                featherFall = p.getEquipment().getBoots().getEnchantmentLevel(Enchantment.PROTECTION_FALL) * 0.5; //reduce fall damage by half heart per feather fall level
            double damangeAmount = ((p.getFallDistance() - 3) / 1.9) - featherFall;
            if (damangeAmount >= 1) //prevent player taking damage they can't see, which just makes a sound
                p.damage(damangeAmount);

            //jump a bit if player is standing on ground and starts climbing
            if (v.getY() > -0.08 && v.getY() < -0.07 && atWall(L1, uuid)) {
                p.setVelocity(v.add(new Vector(0, 0.25, 0)));
            }

            if (StaminaBar.registeredBars.get(uuid).getProgress() > 0) {
                if (atWall(L1, uuid)) {
                    isClimbing.put(uuid, true);
                    p.setAllowFlight(true);
                    p.setFlying(true);
                } else {
                    isClimbing.put(uuid, false);
                    cooldown.put(uuid, System.currentTimeMillis());
                }
            }
            p.setFlySpeed(0.025f);
        }
    }

    @EventHandler()
    public void onLeftClick(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (allowClimb(p) && cooldownComplete(uuid) && isClimbing.containsKey(uuid)) {
            //set a cooldown for player not to be able to wall jump right away
            cooldown.put(uuid, System.currentTimeMillis() + WALL_JUMP_COOLDOWN);
            BossBar b = StaminaBar.registeredBars.get(uuid);

            //Find left clicked block (in adventure mode)
            List<Block> blocks = p.getLastTwoTargetBlocks(null, 4); //Get two connected blocks player is looking at

            if (blocks.size() < 2 || blocks.get(0).isLiquid())
                return;

            //Horizontal leap
            Material blockType = blocks.get(1).getType();
            if (leftClicked(blockType)) { //Make sure target block isn't in blacklist
                Vector direction = p.getLocation().getDirection();

                double x = direction.getX();
                double y = direction.getY();
//                y *= 0.8;
//                y += Math.signum(y) * 0.7;
                double z = direction.getZ();

                if (!atWall(p.getLocation(), uuid)) { //if not at a wall (i.e. double jump)
                    StaminaBar.removeProgress(0.275, b); //take away more stamina when in the air
                    p.setVelocity(p.getVelocity().setX(x / 1.8).setY(y / 2 + 0.3).setZ(z / 1.8)); //shorter leap
                    cooldown.put(uuid, 0L);
                } else {
                    StaminaBar.removeProgress(0.2, b);
                    p.setVelocity(p.getVelocity().setX(x / 1.8).setY(y / 1).setZ(z / 1.8));
                }
            }
        }
    }

    private boolean allowClimb(Player p) { //does player meet all requirements to be able to climb
        UUID uuid = p.getUniqueId();
        if (!StaminaBar.toggled.contains(uuid) //If player is not in the blacklist, they have their stamina toggled on
                && canClimb.get(uuid) //Check if player allowed to climb
                && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) //Check if player in survival or adventure mode
//            && p.getInventory().getItemInMainHand().getType().equals(Material.AIR)) //Make sure player is holding nothing in hand
            return true;
        return false;
    }

    private boolean rightClicked(PlayerInteractEvent e) { //did player do a valid right click
        if (e.getClickedBlock() == null)
            return false;

        Player p = e.getPlayer();
        Material block = e.getClickedBlock().getType();

        if (BLOCK_BLACKLIST.contains(block)) {
            cooldown.put(p.getUniqueId(), System.currentTimeMillis() + JUMP_COOLDOWN); //Set a cooldown of 2 ticks
            return false;
        } else {
            for (String interactable : BLOCK_BLACKLIST_GENERALIZED) {
                if (block.toString().contains(interactable)) {
                    cooldown.put(p.getUniqueId(), System.currentTimeMillis() + JUMP_COOLDOWN); //Set a cooldown of 2 ticks
                    return false;
                }
            }
        }
        if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.getHand().equals(EquipmentSlot.HAND) //Check right click, e.getHand makes event only trigger once, otherwise spigot calls it twice
                && block.isSolid())
            return true;
        return false;
    }

    private boolean leftClicked(Material block) { //did player do a valid left click
        if (BLOCK_BLACKLIST.contains(block)) //If clicked block is in blacklist, return false
            return false;
        return true;
    }

    private boolean cooldownComplete(UUID uuid) { //is the click cooldown complete
        long playerCooldown = cooldown.get(uuid);
        if (playerCooldown <= System.currentTimeMillis()) //If time indicated in cooldown reached, cooldown is complete
            return true;
        return false;
    }
}
