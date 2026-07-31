rootProject.name = "odeon-clients"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// shared    → modelos + cliente HTTP + repositório (sem UI)
// composeApp→ UI Compose Multiplatform: celular Android + iOS
// tv        → Android TV; UI própria (foco por D-pad), mesmo `shared`
include(":shared")
include(":composeApp")
include(":tv")
