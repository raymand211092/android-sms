plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")
    id("kotlin-parcelize")
    id("maven-publish")
}

object Version {
    const val major = 0
    const val minor = 1
    const val patch = 1
}

android {
    compileSdk = 30
    buildToolsVersion = "30.0.3"

    defaultConfig {
        minSdk = 21
        targetSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    implementation("com.klinkerapps:android-smsmms:5.2.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.0")
    implementation("androidx.core:core-ktx:1.5.0")
    implementation("com.google.code.gson:gson:2.8.7")

    testImplementation("junit:junit:4.+")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}
