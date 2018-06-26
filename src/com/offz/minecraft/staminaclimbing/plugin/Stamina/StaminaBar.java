package com.offz.minecraft.staminaclimbing.plugin.Stamina;

import com.offz.minecraft.staminaclimbing.plugin.StaminaAndClimbing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

import java.util.*;

public class StaminaBar implements Listener {

    public static List<UUID> toggled = new ArrayList<>();

    public static Map<UUID, BossBar> registeredBars = new HashMap<>();
    public void removeBossBarProgress(double amount, Player p, BossBar b){
        double progress = b.getProgress();
        if(progress - amount >= 0) {
            b.setProgress(b.getProgress() - amount);
        }else {
            b.setProgress(0);
        }
    }
    @EventHandler
    public void onDisable(PluginDisableEvent e){
        for (BossBar b : registeredBars.values()) {
            b.removeAll();
        }
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e){
        if(!StaminaBar.toggled.contains(e.getPlayer().getUniqueId())) return;
        registerBar(e.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e){
        if(!StaminaBar.toggled.contains(e.getPlayer().getUniqueId())) return;
        registeredBars.get(e.getPlayer().getUniqueId()).removeAll();
        registeredBars.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        if(!StaminaBar.toggled.contains(e.getPlayer().getUniqueId())) return;
        Player p = e.getPlayer();
        if(registeredBars.get(p.getUniqueId()) == null){
            registerBar(e.getPlayer());
        }
        BossBar b = registeredBars.get(p.getUniqueId());
        if (p.isSprinting() && b.getProgress() >= 0.1 + 0.0035) {
            removeBossBarProgress(0.0035, p, b);
        }else if (p.isSneaking()) {
        } else {
            removeBossBarProgress(0.00025, p, b);
        }
    }
    @EventHandler
    public void onPlayerFall(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Player p = (Player) e.getEntity();
            BossBar b = registeredBars.get(p.getUniqueId());

            removeBossBarProgress((p.getFallDistance() * (p.getFallDistance()/2)) / 112.5, p, b);
        }
    }
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e){
        if(!StaminaBar.toggled.contains(e.getEntity().getUniqueId())) return;
        Player p = e.getEntity();
        BossBar b = registeredBars.get(p.getUniqueId());
        b.setProgress(1);
    }
    public static void registerBar(Player p){
        BossBar b = Bukkit.createBossBar(ChatColor.BOLD+"Stamina", BarColor.BLUE, BarStyle.SEGMENTED_10);
        b.addPlayer(p);
        registeredBars.put(p.getUniqueId(), b);
    }
}