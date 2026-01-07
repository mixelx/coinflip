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

    // TON blockchain - official ton4j library
    // https://github.com/ton-blockchain/ton4j
    implementation("org.ton.ton4j:toncenter:1.3.5")
    implementation("org.ton.ton4j:smartcontract:1.3.5")
    implementation("org.ton.ton4j:cell:1.3.5")

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


