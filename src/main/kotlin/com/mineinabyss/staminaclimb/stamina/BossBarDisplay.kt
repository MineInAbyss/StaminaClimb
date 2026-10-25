package com.mineinabyss.staminaclimb.stamina

import com.mineinabyss.idofront.textcomponents.miniMsg
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

object BossBarDisplay : StaminaDisplay {
    private val bars: Object2ObjectOpenHashMap<UUID, BossBar> = Object2ObjectOpenHashMap()

    private val baseName = "<b>Stamina".miniMsg()
    private val alertName = "<red><b>Stamina".miniMsg()

    override fun show(player: Player, stamina: PlayerStamina) {
        hide(player.uniqueId)
        val bossBar = BossBar.bossBar(baseName, 1f, StaminaBar.conf.baseBarColor, StaminaBar.conf.baseOverlay)
        bossBar.addListener(object : BossBar.Listener {
            override fun bossBarProgressChanged(bar: BossBar, oldProgress: Float, newProgress: Float) {
                if (newProgress < 1f)
                    player.showBossBar(bar)
                else player.hideBossBar(bar)
            }
        })
        bars[player.uniqueId] = bossBar
        update(player, stamina)
    }

    override fun update(player: Player, stamina: PlayerStamina) {
        val bar = bars[player.uniqueId] ?: return
        bar.progress(stamina.progress)
        bar.color(if (stamina.alert) BossBar.Color.RED else StaminaBar.conf.baseBarColor)
        bar.name(if (stamina.alert) alertName else baseName)
    }

    override fun hide(uuid: UUID) {
        bars.remove(uuid)?.let { Bukkit.getServer().hideBossBar(it) }
    }

    override fun hideAll() {
        bars.values.forEach { Bukkit.getServer().hideBossBar(it) }
        bars.clear()
    }
}
