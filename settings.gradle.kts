pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Shining"
// This repo has both; the public app repo (Nisatech-org/shining-app) has no server/ and the
// backend repo (Nisatech-org/shining-backend) no app/, so each is built where it's there.
// See deploy/publish-app.sh and deploy/push-backend.sh.
if (file("app").isDirectory) include(":app")
if (file("server").isDirectory) include(":server")
