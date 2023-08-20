
val idofrontVersion: String by project

plugins {
    id("com.mineinabyss.conventions.kotlin.jvm")
    id("com.mineinabyss.conventions.papermc")
    id("com.mineinabyss.conventions.nms")
    id("com.mineinabyss.conventions.publication")
    id("com.mineinabyss.conventions.copyjar")
    id("com.mineinabyss.conventions.autoversion")
    alias(libs.plugins.kotlinx.serialization)
}

repositories {
    maven("https://repo.mineinabyss.com/snapshots")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.kotlinx.serialization.kaml) {
        exclude(group = "org.jetbrains.kotlin")
    }
    compileOnly(libs.kotlinx.coroutines)
    compileOnly(libs.minecraft.mccoroutine)
    compileOnly(staminaLibs.geary.papermc)
    compileOnly(staminaLibs.bonehurtingjuice)

    implementation(libs.bundles.idofront.core)
    implementation(libs.idofront.nms)
}
