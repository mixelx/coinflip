plugins {
    id("io.micronaut.application") version "4.6.1"
    id("com.gradleup.shadow") version "8.3.9"
    id("io.micronaut.aot") version "4.6.1"
    id("io.micronaut.library") version "4.6.1" apply false
}

version = "0.1"
group = "com.example"

repositories {
    mavenCentral()
}

dependencies {
    // Annotation processors
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut.data:micronaut-data-processor")

    // Core
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut.serde:micronaut-serde-jackson")

    // TON blockchain - ton4j library for wallet operations
    implementation("io.github.neodix42:smartcontract:0.9.0")
    implementation("io.github.neodix42:emulator:0.9.0")

    // Data JDBC
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // Flyway
    implementation("io.micronaut.flyway:micronaut-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Logging
    runtimeOnly("ch.qos.logback:logback-classic")

    runtimeOnly("org.yaml:snakeyaml")

    // Test
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


application {
    mainClass = "com.example.Application"
}
java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}


graalvmNative.toolchainDetection = false

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.example.*")
    }
    aot {
        // Please review carefully the optimizations enabled below
        // Check https://micronaut-projects.github.io/micronaut-aot/latest/guide/ for more details
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }
}


tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "21"
}


