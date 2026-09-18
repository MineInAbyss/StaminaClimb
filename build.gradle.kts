import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription.RelativeLoadOrder.BEFORE

plugins {
    alias(miaLibs.plugins.mia.kotlin.jvm)
    alias(miaLibs.plugins.mia.papermc)
    alias(miaLibs.plugins.mia.nms)
    alias(miaLibs.plugins.mia.publication)
    alias(miaLibs.plugins.mia.copyjar)
    alias(miaLibs.plugins.mia.autoversion)
    alias(miaLibs.plugins.kotlinx.serialization)
}

repositories {
    maven("https://repo.mineinabyss.com/snapshots")
    maven("https://jitpack.io")
    mavenLocal()
}

dependencies {
    compileOnly(miaLibs.kotlin.stdlib)
    compileOnly(miaLibs.kotlinx.serialization.kaml) {
        exclude(group = "org.jetbrains.kotlin")
    }
    compileOnly(miaLibs.kotlinx.coroutines)
    compileOnly(miaLibs.minecraft.mccoroutine)
    compileOnly(miaLibs.geary.papermc)

    compileOnly(miaLibs.bundles.idofront.core)
    compileOnly(miaLibs.idofront.nms)
}

paper {
    name = "StaminaClimb"
    main = "com.mineinabyss.staminaclimb.StaminaClimbPlugin"
    authors = listOf("Offz")
    apiVersion = "1.21"
    prefix = "SnC"
    description = "A mod that lets you climb and get exhausted"

    permissions {
        register("staminaclimb.climb") {
            description = "Toggle the stamina and climbing system for yourself"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("staminaclimb.reload") {
            description = "Reload the StaminaClimb config"
            default = BukkitPluginDescription.Permission.Default.OP
        }
    }

    serverDependencies {
        register("Idofront") {
            required = true
            load = BEFORE
            joinClasspath = true
        }
        register("Geary") {
            required = true
            load = BEFORE
            joinClasspath = true
        }
    }
}
