import org.jetbrains.kotlin.gradle.tasks.*
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.nio.file.attribute.FileTime
import java.nio.file.Files
import java.nio.file.Paths

val junitJupiterVersion = "5.6.3"
val ktorVersion = "1.4.3"
val micrometerVersion = "1.3.16"
val kafkaVersion = "2.4.0"
val slf4jVersion = "1.7.30"
val logbackVersion = "1.2.3"
val logstashEncoderVersion = "6.5"
val serializerVersion = "1.0.1"

group = "no.nav.helse"

plugins {
    val kotlinVersion = "1.4.21"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    application
}

repositories {
    mavenCentral()
    maven("http://packages.confluent.io/maven/")
    jcenter()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializerVersion")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializerVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core") {
        isTransitive = true
    }


    implementation("io.ktor:ktor-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")

    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")



    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "junit")
    }
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion") {
        exclude(group = "junit")
    }

    testImplementation("org.awaitility:awaitility:4.0.1")
    testImplementation("io.mockk:mockk:1.10.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
    targetCompatibility = JavaVersion.VERSION_12
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("app")

    manifest {
        attributes["Main-Class"] = "no.nav.helse.AppKt"
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
            it.name
        }
    }


    fun copyJarsWithDeterministicTimestamps() {
        val epoch = FileTime.fromMillis(0)

        fun setFileTimeToEpoch(path: java.nio.file.Path) {
            Files.setAttribute(path, "creationTime", epoch)
            Files.setAttribute(path, "basic:creationTime", epoch)
            Files.setLastModifiedTime(path, epoch)
        }

        configurations.runtimeClasspath.get().forEach {
            val file = File("$buildDir/libs/${it.name}")
            println(file)
            if (!file.exists())
                it.copyTo(file)
            val path = Paths.get(file.absolutePath)
            setFileTimeToEpoch(path)
        }

        println("writing app2")
        val libDir = "$buildDir/libs"
        val pathStringToAppJar = "$libDir/app.jar"
        val pathStringToApp2Jar = "$libDir/app2.jar"
        val pathToAppJar = Paths.get(libDir,"app.jar")
        //val pathToApp2Jar = Paths.get(libDir,"app2.jar")
        val appJar = File(pathStringToAppJar)
        val app2Jar = File(pathStringToApp2Jar)

        ZipOutputStream(app2Jar.outputStream()).use { o ->
            ZipInputStream(appJar.inputStream()).use { i ->
                val buffer = ByteArray(8192)
                var z = i.nextEntry
                while (z != null) {
                    o.putNextEntry(
                        z.setCreationTime(epoch)
                            .setLastAccessTime(epoch)
                            .setLastModifiedTime(epoch)
                    )
                    var n: Int
                    while (i.read(buffer, 0, buffer.size).also { n = it } > 0) {
                        o.write(buffer, 0, n)
                    }
                    z = i.nextEntry
                }
            }
        }

        Files.delete(pathToAppJar)
        File(pathStringToApp2Jar).renameTo(File(pathStringToAppJar))
        setFileTimeToEpoch(pathToAppJar)

    }

    doLast {
        copyJarsWithDeterministicTimestamps()
    }

}

application {
    mainClassName = "no.nav.helse.AppKt"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "6.1.1"
}
