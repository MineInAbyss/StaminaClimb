package com.offz.minecraft.staminaclimbing.plugin.Stamina;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.UUID;

public class StaminaTask extends BukkitRunnable {
    @Override
    public void run() {
        for (UUID uuid : StaminaBar.registeredBars.keySet()) { //Regenerate stamina for every BossBar
            Player p = Bukkit.getPlayer(uuid);
            BossBar b = StaminaBar.registeredBars.get(uuid);
            double progress = b.getProgress();

            if (progress + 0.0025 <= 1) {
                b.setVisible(true);
                b.setProgress(progress + 0.0025);
            } else {
                b.setVisible(false);
                b.setProgress(1);
            }

            if (progress <= 0.1) { //Changing bar colors and effects on player depending on its progress
                b.setColor(BarColor.RED);

                p.setSaturation(0);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 200, 2, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 2, false, false));
            } else if (progress <= 0.5) {
                b.setColor(BarColor.YELLOW);
            } else {
                b.setColor(BarColor.GREEN);
            }
        }
    }
}
