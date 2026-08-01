import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription.RelativeLoadOrder.BEFORE

plugins {
    alias(idofrontLibs.plugins.mia.kotlin.jvm)
    alias(idofrontLibs.plugins.mia.papermc)
    alias(idofrontLibs.plugins.mia.nms)
    alias(idofrontLibs.plugins.mia.publication)
    alias(idofrontLibs.plugins.mia.copyjar)
    alias(idofrontLibs.plugins.mia.autoversion)
    alias(idofrontLibs.plugins.kotlinx.serialization)
}

repositories {
    maven("https://repo.mineinabyss.com/snapshots")
    maven("https://jitpack.io")
    mavenLocal()
}

dependencies {
    compileOnly(idofrontLibs.kotlin.stdlib)
    compileOnly(idofrontLibs.kotlinx.serialization.kaml) {
        exclude(group = "org.jetbrains.kotlin")
    }
    compileOnly(idofrontLibs.kotlinx.coroutines)
    compileOnly(idofrontLibs.minecraft.mccoroutine)
    compileOnly(idofrontLibs.geary.papermc)

    compileOnly(idofrontLibs.bundles.idofront.core)
    compileOnly(idofrontLibs.idofront.nms)
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
