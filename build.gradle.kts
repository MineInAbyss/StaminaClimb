
val idofrontVersion: String by project

plugins {
    id("com.mineinabyss.conventions.kotlin")
    id("com.mineinabyss.conventions.papermc")
    id("com.mineinabyss.conventions.nms")
    id("com.mineinabyss.conventions.publication")
    id("com.mineinabyss.conventions.copyjar")
    kotlin("plugin.serialization")
}

repositories {
    mavenLocal()
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.kotlinx.serialization.kaml) {
        exclude(group = "org.jetbrains.kotlin")
    }
    compileOnly(libs.kotlinx.coroutines)
    compileOnly(libs.minecraft.mccoroutine)
    compileOnly("com.mineinabyss:bonehurtingjuice:1.3.11")

    implementation(libs.idofront.core)
    implementation(libs.idofront.nms)
}

tasks {
    shadowJar {
        minimize()
    }
}
