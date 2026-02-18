plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "com.microsoft.jmeter"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

// JMeter version to compile against
val jmeterVersion = "5.6.3"

dependencies {
    // JMeter core — provided at runtime by JMeter itself, not bundled in the plugin JAR
    compileOnly("org.apache.jmeter:ApacheJMeter_core:$jmeterVersion")

    // SLF4J — provided at runtime by JMeter
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    // AutoService — annotation processor for META-INF/services generation
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")

    // Azure Identity for authentication
    implementation("com.azure:azure-identity:1.14.2")

    // Azure Resource Manager for listing subscriptions
    implementation("com.azure.resourcemanager:azure-resourcemanager-resources:2.43.0")

    // Azure Resource Manager for listing Azure Load Testing resources
    implementation("com.azure.resourcemanager:azure-resourcemanager-loadtesting:1.0.0")

    // Azure Load Testing data-plane SDK for creating and running tests
    implementation("com.azure:azure-developer-loadtesting:1.0.17")

    // Jackson for JSON processing
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    // JavaFX WebView for embedded browser rendering
    val javafxVersion = "21.0.5"
    val javafxPlatform = when {
        org.gradle.internal.os.OperatingSystem.current().isWindows -> "win"
        org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "mac"
        else -> "linux"
    }
    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-web:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-swing:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-media:$javafxVersion:$javafxPlatform")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}

// Build a fat/shadow JAR that bundles all Azure SDK dependencies
// but excludes JMeter classes (which are provided at runtime)
tasks.shadowJar {
    archiveClassifier.set("")
    // Exclude JMeter classes — they are provided by JMeter at runtime
    dependencies {
        exclude(dependency("org.apache.jmeter:.*"))
        exclude(dependency("org.slf4j:.*"))
    }
    // Relocate Azure & Netty packages to avoid classpath conflicts
    relocate("com.azure", "shadow.com.azure")
    relocate("io.netty", "shadow.io.netty")
    relocate("reactor.", "shadow.reactor.")
    relocate("com.fasterxml.jackson", "shadow.com.fasterxml.jackson")
    relocate("com.nimbusds", "shadow.com.nimbusds")
    relocate("com.microsoft.aad", "shadow.com.microsoft.aad")
    relocate("net.minidev", "shadow.net.minidev")
    // Do NOT relocate JavaFX — it requires native libraries at fixed package paths
    mergeServiceFiles()
}

// Make the default "jar" task produce the shadow JAR
tasks.jar {
    enabled = false
}
tasks.build {
    dependsOn(tasks.shadowJar)
}
