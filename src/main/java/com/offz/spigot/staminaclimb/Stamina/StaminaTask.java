package com.offz.spigot.staminaclimb.Stamina;

import com.offz.spigot.staminaclimb.Climbing.ClimbBehaviour;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;

public class StaminaTask extends BukkitRunnable {
    @Override
    public void run() {
        for (UUID uuid : StaminaBar.registeredBars.keySet()) { //Regenerate stamina for every BossBar
            Player p = Bukkit.getPlayer(uuid);
            BossBar b = StaminaBar.registeredBars.get(uuid);
            double progress = b.getProgress();
            if (progress + 0.01 <= 1) {
                b.setVisible(true);
                if (p.isOnGround() || p.getGameMode().equals(GameMode.CREATIVE) || p.getVehicle() != null || !ClimbBehaviour.canClimb.get(uuid))
                    b.setProgress(progress + 0.01);
            } else {
                b.setVisible(false);
                if (p.isOnGround() || !ClimbBehaviour.canClimb.get(uuid))
                    b.setProgress(1);
            }

            if (progress <= 0.02) { //Changing bar colors and effects on player depending on its progress
                b.setColor(BarColor.RED);
                b.setTitle(ChatColor.translateAlternateColorCodes('&', "&c&lStamina")); //Make Stamina title red
                if (ClimbBehaviour.isClimbing.containsKey(uuid)) {
                    ClimbBehaviour.stopClimbing(p);
                }

                ClimbBehaviour.canClimb.put(uuid, false); //If player reaches red zone, they can't climb until they get back in green zone
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 110, 2, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 110, 2, false, false));
            } else if (progress < 1) {
                if (!ClimbBehaviour.canClimb.get(uuid)) { //Keep Stamina Bar red even in yellow zone while it's regenerating
                    b.setColor(BarColor.RED);
                    continue;
                }
            } else {
                b.setColor(BarColor.GREEN);
                b.setTitle(ChatColor.BOLD + "Stamina");
                ClimbBehaviour.canClimb.put(uuid, true);
            }
        }
        for (UUID uuid : ClimbBehaviour.isClimbing.keySet()) {
            //im not sure if this is needed but we're using a ConcurrentHashMap to solve some errors, so I assume it's
            //possible for the real map to not contain something we're currently iterating over
            if (!ClimbBehaviour.isClimbing.containsKey(uuid))
                return;

            Player p = Bukkit.getPlayer(uuid);
            Vector direction = p.getLocation().getDirection();
            Vector v = p.getVelocity();

            boolean isClimbing = ClimbBehaviour.isClimbing.get(uuid);

            //if climbing in creative, stop climbing but keep flight
            if(p.getGameMode().equals(GameMode.CREATIVE)) {
                ClimbBehaviour.stopClimbing(p);
                p.setAllowFlight(true);
                p.setFlying(true);
                continue;
            }

            if (!p.isFlying() && isClimbing || p.getFallDistance() > 5) {
                ClimbBehaviour.stopClimbing(p);
                continue;
            }

            if (ClimbBehaviour.atWall(p.getLocation(), uuid)) {
                if (ClimbBehaviour.cooldown.get(uuid) <= System.currentTimeMillis())
                    ClimbBehaviour.cooldown.put(uuid, System.currentTimeMillis());
                p.setAllowFlight(true);
                p.setFlying(true);
                ClimbBehaviour.isClimbing.put(uuid, true);
            } else {
                if (isClimbing) {
                    p.setVelocity(new Vector(v.getX() + direction.getX() / 20, v.getY(), v.getZ() + direction.getZ() / 20));
                    ClimbBehaviour.isClimbing.put(uuid, false);
                    ClimbBehaviour.cooldown.put(uuid, System.currentTimeMillis());
                    p.setFlying(false);
                    p.setAllowFlight(false);
                } else if (ClimbBehaviour.cooldown.get(uuid) + 400 <= System.currentTimeMillis()) {
                    ClimbBehaviour.stopClimbing(p);
                    continue;
                }
            }
            if (ClimbBehaviour.isClimbing.get(uuid))
                StaminaBar.removeProgress(0.008, uuid);
        }
    }
}
