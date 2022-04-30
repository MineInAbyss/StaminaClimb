pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.mineinabyss.com/releases")
        maven("https://papermc.io/repo/repository/maven-public/")
        google()
        mavenLocal()
    }

    plugins {
        val kotlinVersion: String by settings
        kotlin("plugin.serialization") version kotlinVersion
    }

    val idofrontVersion: String by settings
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.mineinabyss.conventions"))
                useVersion(idofrontVersion)
        }
    }
}

dependencyResolutionManagement {
    val idofrontVersion: String by settings

    repositories {
        maven("https://repo.mineinabyss.com/releases")
    }

    versionCatalogs {
        create("libs") {
            from("com.mineinabyss:catalog:$idofrontVersion")
        }
    }
}

rootProject.name = "stamina-climb"
