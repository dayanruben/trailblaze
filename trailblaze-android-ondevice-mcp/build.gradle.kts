plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.vanniktech.maven.publish)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.dependency.guard)
  alias(libs.plugins.spotless)
}

android {
  namespace = "xyz.block.trailblaze.android.mcp.ondevice"
  compileSdk = 35
  defaultConfig {
    minSdk = 26
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    testApplicationId = "xyz.block.trailblaze.runner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  lint {
    abortOnError = false
  }

  packaging {
    exclude("META-INF/INDEX.LIST")
    exclude("META-INF/AL2.0")
    exclude("META-INF/LICENSE.md")
    exclude("META-INF/LICENSE-notice.md")
    exclude("META-INF/LGPL2.1")
    exclude("META-INF/io.netty.versions.properties")
  }

  @Suppress("UnstableApiUsage")
  testOptions {
    unitTests.all {
      it.useJUnitPlatform()
    }
  }
}

dependencies {
  implementation(project(":trailblaze-android"))
  implementation(project(":trailblaze-agent"))
  implementation(project(":trailblaze-server"))

  implementation(libs.ktor.server.core.jvm)
  implementation(libs.mcp.sdk)
  implementation(libs.coroutines)
  implementation(libs.okhttp)
  implementation(libs.ktor.server.cio)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.koog.prompt.executor.openai)
  implementation(libs.koog.prompt.executor.ollama)
  implementation(libs.androidx.test.monitor)

  androidTestImplementation(libs.androidx.test.runner)
}

dependencyGuard {
  configuration("debugRuntimeClasspath") {
    modules = true
  }
}
