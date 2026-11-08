pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven { url = java.net.URI.create("https://repo1.maven.org/maven2") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = java.net.URI.create("https://repo1.maven.org/maven2") }
        mavenCentral()
    }
}

rootProject.name = "LockWeb"
include(":core-filter")
include(":app")
