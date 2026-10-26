pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Optional transport for environments where Google's Maven TLS is unavailable.
        // Versions stay pinned in libs.versions.toml; official repositories remain the default.
        if (providers.gradleProperty("fitlog.mavenMirror").orNull == "true") {
            maven("https://maven.aliyun.com/repository/google") { content { includeGroupByRegex("androidx\\..*") } }
            maven("https://maven.aliyun.com/repository/central")
        }
        google(); mavenCentral()
    }
}
rootProject.name = "LiftingLoop"
include(":app")
