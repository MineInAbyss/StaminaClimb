package com.offz.minecraft.staminaclimbing.plugin.Stamina;

import com.offz.minecraft.staminaclimbing.plugin.Climbing.ClimbBehaviour;
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
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.util.Vector;

import java.util.*;

public class StaminaBar implements Listener {

    public static List<UUID> toggled = new ArrayList<>();
    public static Map<UUID, BossBar> registeredBars = new HashMap<>();
    public static Map<UUID, Double> velocities = new HashMap<>();

    @EventHandler
    public void onDisable(PluginDisableEvent e) {
        for (BossBar b : registeredBars.values()) {
            b.removeAll();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        registerBar(e.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        unregisterBar(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!StaminaBar.toggled.contains(uuid)) return; //Only run if player has system turned on

        double vel = p.getVelocity().getY();
        if (vel < -0.1) {
            velocities.put(uuid, vel);
        }

        /*if(registeredBars.get(p.getUniqueId()) == null)
            registerBar(e.getPlayer());

        BossBar b = registeredBars.get(p.getUniqueId());

        if (p.isSprinting() && b.getProgress() >= 0.1 + 0.0035) {
            removeProgress(0.0035, b);
        } else if (p.isSneaking()){ //Do nothing
        } else {
            removeProgress(0.00025, b);
        }*/ //Stamina depletion for movement has been turned off as it may be replaced by a different system

        Location loc = e.getFrom();
        Location to = e.getTo();

        //if player is climbing and has moved
        if (ClimbBehaviour.isClimbing.containsKey(uuid) && (loc.getX() != to.getX() || loc.getY() != to.getY() || loc.getZ() != to.getZ()) && p.getVelocity().equals(new Vector(0, 0, 0))) {
            StaminaBar.removeProgress(0.005, uuid);
        }
    }

    @EventHandler
    public void onPlayerFall(EntityDamageEvent e) { //Remove stamina from player falls
        if (e.getEntity() instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Player p = (Player) e.getEntity();
            UUID uuid = p.getUniqueId();

            if (!StaminaBar.toggled.contains(uuid)) return;
            BossBar b = registeredBars.get(uuid);

            double threshold = 0.6;
            double multiplier = 11.0;
            double exponent = 1.1;
            double vel = velocities.get(uuid);

            b.setVisible(true);

            if (!velocities.containsKey(uuid)) {
                e.setCancelled(true);
                return;
            }

            if (vel > -threshold) {
                e.setCancelled(true);
                removeProgress(0.1 / 15, b);
                return;
            }

            double damage = Math.pow((vel + threshold) * -multiplier, exponent);
            e.setDamage(damage);
            removeProgress(damage / 15, b); //Taking 15 health of damage reduces stamina fully
            p.setVelocity(p.getVelocity().setY(0)); //stop weird effects from levitation
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        UUID uuid = p.getUniqueId();

        if (!StaminaBar.toggled.contains(uuid)) return;

        BossBar b = registeredBars.get(uuid);
        b.setProgress(1);
    }

    public static void registerBar(Player p) {
        UUID uuid = p.getUniqueId();

        if (!StaminaBar.toggled.contains(uuid)) return;

        BossBar b = Bukkit.createBossBar(ChatColor.BOLD + "Stamina", BarColor.GREEN, BarStyle.SEGMENTED_10);
        b.addPlayer(p);
        registeredBars.put(uuid, b);
    }

    public static void unregisterBar(UUID uuid) {
        if (!StaminaBar.toggled.contains(uuid)) return;

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
}