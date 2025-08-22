plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.spotless)
  alias(libs.plugins.dependency.guard)
  application
}

application {
  mainClass.set("xyz.block.trailblaze.logs.server.LogsServerMainKt")
}

dependencies {
  api(libs.ktor.server.core)
  api(libs.ktor.server.netty) {
    exclude(group = "io.netty", module = "netty-codec-http2")
    because("Maestro has binary incompatible code with the new version of netty-codec-http2, so we use the old version from maestro")
  }
  api("io.netty:netty-codec-http2:4.1.79.Final") {
    because("Maestro has binary incompatible code with the new version of netty-codec-http2, so we use the old version from maestro")
  }

  implementation(project(":trailblaze-common"))
  implementation(project(":trailblaze-report"))
  implementation(project(":trailblaze-yaml"))

  implementation(libs.freemarker)
  implementation(libs.okhttp)
  implementation(libs.ktor.server.core.jvm)
  implementation(libs.ktor.network.tls.certificates)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.logging)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.cors)
  implementation(libs.ktor.server.freemarker)
  implementation(libs.ktor.server.websockets)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.coroutines)
  implementation(libs.koog.agents.tools)
  implementation(libs.kotlinx.datetime)
  implementation(libs.ktor.http)
  implementation(libs.ktor.serialization)
  implementation(libs.ktor.utils)
  implementation(libs.ktor.websockets)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.maestro.orchestra.models) { isTransitive = false }
  implementation(libs.mcp.sdk)

  runtimeOnly(libs.jackson.module.kotlin)
  runtimeOnly(libs.slf4j.simple)

  testImplementation(libs.ktor.client.okhttp)

  testRuntimeOnly(libs.kotlin.test.junit4)
}

tasks.test {
  useJUnit()
}

dependencyGuard {
  configuration("runtimeClasspath")
}
