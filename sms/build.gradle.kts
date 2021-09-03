plugins {
    id("com.android.library")
    id("kotlin-android")
    kotlin("android")
    kotlin("kapt")
    id("maven-publish")
}

object Version {
    const val major = 0
    const val minor = 1
    const val patch = 27
}

android {
    compileSdk = 30
    buildToolsVersion = "30.0.3"

    defaultConfig {
        minSdk = 23
        targetSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        useIR = true
    }

    packagingOptions {
        resources.excludes.addAll(
            listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        )
    }
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                url = uri("https://gitlab.com/api/v4/projects/27629761/packages/maven")
                credentials(HttpHeaderCredentials::class) {
                    name = "Deploy-Token"
                    value = System.getenv("CI_TOKEN")
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            }
        }
        publications {
            create<MavenPublication>("gitlab") {
                from(components["release"])
                groupId = "com.beeper"
                artifactId = "android-sms"
                version = "${Version.major}.${Version.minor}.${Version.patch}"
                pom {
                    name.set("Beeper SMS Bridge")
                    description.set("Matrix-SMS bridge for Android")
                    url.set("https://beeper.com")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://gitlab.com/beeper/android-sms.git")
                        developerConnection.set("scm:git:ssh://gitlab.com/beeper/android-sms.git")
                        url.set("https://gitlab.com/beeper/android-sms.git")
                    }
                }
            }
        }
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:${Versions.work}")
    implementation("com.gitlab.beeper:android-smsmms:a114e22")
    implementation("org.yaml:snakeyaml:1.29")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.1")
    implementation("androidx.core:core-ktx:1.6.0")
    implementation("com.google.code.gson:gson:2.8.7")
    implementation("com.jakewharton.timber:timber:4.7.1")

    testImplementation("junit:junit:4.+")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}
