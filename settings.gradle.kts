dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.gitlab.beeper")
            }
        }
    }
}
rootProject.name = "Beeper SMS Bridge"
include(":app")
include(":sms")
