package com.offz.spigot.staminaclimb.Stamina;

import com.offz.spigot.staminaclimb.Climbing.ClimbBehaviour;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.*;

public class StaminaBar implements Listener {

    public static List<UUID> toggled = new ArrayList<>();
    public static Map<UUID, BossBar> registeredBars = new HashMap<>();
    public static Map<UUID, Double> velocities = new HashMap<>();

    public static void registerBar(Player p) {
        UUID uuid = p.getUniqueId();
        ClimbBehaviour.cooldown.put(uuid, System.currentTimeMillis());
        ClimbBehaviour.canClimb.put(uuid, true);
        StaminaBar.toggled.remove(uuid);

        BossBar b = Bukkit.createBossBar(ChatColor.BOLD + "Stamina", BarColor.GREEN, BarStyle.SEGMENTED_10);
        b.addPlayer(p);
        registeredBars.put(uuid, b);
    }

    public static void unregisterBar(UUID uuid) {
        ClimbBehaviour.cooldown.remove(uuid);
        registeredBars.get(uuid).removeAll();
        registeredBars.remove(uuid);
    }

    public static void removeProgress(double amount, BossBar b) { //Removes double amount from BossBar b's progress
        double progress = b.getProgress();
        if (progress - amount >= 0)
            b.setProgress(progress - amount);
        else
            b.setProgress(0);
    }

    public static void removeProgress(double amount, UUID uuid) { //Removes double amount from BossBar b's progress
        BossBar b = StaminaBar.registeredBars.get(uuid);
        double progress = b.getProgress();
        if (progress - amount >= 0)
            b.setProgress(progress - amount);
        else
            b.setProgress(0);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        registerBar(e.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        unregisterBar(uuid);
        if (ClimbBehaviour.isClimbing.containsKey(uuid))
            ClimbBehaviour.stopClimbing(p);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (StaminaBar.toggled.contains(uuid)) return; //Only run if player has system turned on

        double vel = p.getVelocity().getY();
        if (vel < -0.1) {
            velocities.put(uuid, vel);
        }

        Location loc = e.getFrom();
        Location to = e.getTo();

        //if player is climbing and has moved
        if (ClimbBehaviour.isClimbing.containsKey(uuid) && ClimbBehaviour.isClimbing.get(uuid) && (loc.getX() != to.getX() || loc.getY() != to.getY() || loc.getZ() != to.getZ()) && p.getVelocity().equals(new Vector(0, 0, 0))) {
            StaminaBar.removeProgress(0.002, uuid);
        }
    }

    @EventHandler
    public void onPlayerFall(EntityDamageEvent e) { //Remove stamina from player falls
        if (e.getEntity() instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Player p = (Player) e.getEntity();
            UUID uuid = p.getUniqueId();

            if (StaminaBar.toggled.contains(uuid) || !velocities.containsKey(uuid))
                return;
            BossBar b = registeredBars.get(uuid);

            double threshold = 0.6;
            double multiplier = 11.0;
            double exponent = 1.1;
            double vel = velocities.get(uuid);

            b.setVisible(true);


            if (vel > -threshold) {
                removeProgress(0.1 / 15, b);
                return;
            }

            double damage = Math.pow((vel + threshold) * -multiplier, exponent);
            e.setDamage(damage);
            removeProgress(damage / 15, b); //Taking 15 health of damage reduces stamina fully
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        UUID uuid = p.getUniqueId();

        if (StaminaBar.toggled.contains(uuid)) return;

        BossBar b = registeredBars.get(uuid);
        b.setProgress(1);
    }
}