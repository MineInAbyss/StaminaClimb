import Com_mineinabyss_conventions_platform_gradle.Deps

val idofrontVersion: String by project

plugins {
    id("com.mineinabyss.conventions.kotlin")
    id("com.mineinabyss.conventions.papermc")
    id("com.mineinabyss.conventions.publication")
    id("com.mineinabyss.conventions.copyjar")
    kotlin("plugin.serialization")
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(Deps.kotlin.stdlib)
    compileOnly(Deps.kotlinx.serialization.kaml) {
        exclude(group = "org.jetbrains.kotlin")
    }
    compileOnly(Deps.minecraft.skedule)
    compileOnly("com.mineinabyss:bonehurtingjuice:1.2.4")

    implementation("com.mineinabyss:idofront:$idofrontVersion")
}

tasks {
    shadowJar {
        archiveBaseName.set("StaminaClimb")
    }
}
