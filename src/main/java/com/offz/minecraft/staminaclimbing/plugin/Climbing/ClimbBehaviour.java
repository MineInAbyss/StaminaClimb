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
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class ClimbBehaviour implements Listener {

    //TODO Make for trapdoors and doors of all types
    private List BLOCK_BLACKLIST = Arrays.asList(Material.OAK_TRAPDOOR, Material.DARK_OAK_DOOR, Material.ACACIA_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR, Material.SPRUCE_DOOR, Material.OAK_DOOR, Material.IRON_DOOR, Material.CHEST, Material.ENDER_CHEST, Material.TRAPPED_CHEST);
    private long JUMP_COOLDOWN = 500; //Milliseconds

    public static Map<UUID, Boolean> canClimb = new HashMap<>();
    public static Map<UUID, Boolean> isClimbing = new HashMap<>();
    public static Map<UUID, Long> cooldown = new HashMap<>();

    @EventHandler()
    public void onRightClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        Location L1 = p.getLocation();

        if (allowClimb(p) && rightClicked(e) && cooldownComplete(uuid) && atWall(L1)) {
            Vector v = p.getVelocity();

            if (!isClimbing.containsKey(uuid) && v.getY() > -0.08 && v.getY() < -0.07) {
                p.setVelocity(v.add(new Vector(0, 0.3, 0)));
            }

            isClimbing.put(uuid, true);
            p.setAllowFlight(true);
            p.setFlying(true);
            p.setFlySpeed(0.03f);
        }
    }

    @EventHandler()
    public void onLeftClick(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (allowClimb(p) && e.getAnimationType().equals(PlayerAnimationType.ARM_SWING) && cooldownComplete(uuid) && isClimbing.containsKey(uuid)) {
            cooldown.put(uuid, System.currentTimeMillis() + 300);
            BossBar b = StaminaBar.registeredBars.get(uuid);

            //Find left clicked block (in adventure mode)
            List<Block> blocks = p.getLastTwoTargetBlocks(null, 4); //Get two connected blocks player is looking at
            if (blocks.get(0).isLiquid())
                return;
            BlockFace blockFace = blocks.get(1).getFace(blocks.get(0)); //Find the face between both of these blocks

            //Horizontal leap
            if (leftClicked(blocks.get(1).getType())) { //Make sure target block isn't in blacklist
                Vector direction = p.getLocation().getDirection();
                p.removePotionEffect(PotionEffectType.LEVITATION);

                double x = direction.getX();
                double y = direction.getY();
                y += Math.signum(y) * 0.5;
                double z = direction.getZ();
                p.setVelocity(p.getVelocity().setX(x / 1.5).setY(y / 1.5).setZ(z / 1.5));

                if (blockFace.equals(BlockFace.UP))
                    p.setVelocity(p.getVelocity().setY(0.5 * Math.signum(direction.getY() + 0.95)));

                StaminaBar.removeProgress(0.3, b); //remove almost a third, allowing for only 3 leaps max
            }
        }
    }

    public static void stopClimbing(Player p) {
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setFlySpeed(0.1f);

        UUID uuid = p.getUniqueId();
        ClimbBehaviour.isClimbing.remove(uuid);
    }

    public static boolean atWall(Location loc) { //checks if player is at climbable wall
        if (loc.getBlock().getType().equals(Material.WATER)) //don't fly if in water
            return false;
        for (int x = -1; x <= 1; x += 2) { //check for block to hang onto in a 2x2x2 area around player
            for (int y = 0; y <= 1; y += 1) {
                for (int z = -1; z <= 1; z += 2) {
                    double checkRange = 0.33;
                    Location to = new Location(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ()).add(x * checkRange, y, z * checkRange);
                    if (to.getBlock().getType().isSolid())
                        return true;
                }
            }
        }
        return false;
    }

    private boolean allowClimb(Player p) { //does player meet all requirements to be able to climb
        UUID uuid = p.getUniqueId();
        if (StaminaBar.toggled.contains(uuid) //Check if player has stamina system on
                && canClimb.get(uuid) //Check if player allowed to climb
                && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) //Check if player in survival or adventure mode
                && p.getInventory().getItemInMainHand().getType().equals(Material.AIR)) //Make sure player is holding nothing in hand
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
        } else if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.getHand().equals(EquipmentSlot.HAND) //Check right click, e.getHand makes event only trigger once, otherwise spigot calls it twice
                && block.isSolid()) //Check if clicked block is solid
            return true;
        return false;
    }

    private boolean leftClicked(Material block) { //did player do a valid left click
        if (BLOCK_BLACKLIST.contains(block) && block.isBlock()) //If clicked block is in blacklist, return false
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