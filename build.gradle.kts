import org.jetbrains.kotlin.gradle.tasks.*
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.nio.file.attribute.FileTime
import java.nio.file.Files
import java.nio.file.Paths
import java.io.File

val junitJupiterVersion = "5.6.3"
val ktorVersion = "1.4.3"
val micrometerVersion = "1.3.16"
val kafkaVersion = "2.4.0"
val slf4jVersion = "1.7.30"
val logbackVersion = "1.2.3"
val logstashEncoderVersion = "6.5"
val serializerVersion = "1.0.1"
val apacheCxfVersion = "3.3.5"
val jakartaActivationVersion = "1.2.2"

group = "no.nav.helse"

plugins {
    val kotlinVersion = "1.4.21"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("com.github.bjornvester.xjc") version "1.3"
    id("no.nils.wsdl2java") version "0.12"
    application
}

repositories {
    mavenCentral()
    maven("http://packages.confluent.io/maven/")
    jcenter()
}



wsdl2java {
    wsdlDir = file("$projectDir/src/main/resources/wsdl")
    wsdlsToGenerate = listOf(
        listOf("-wsdlLocation", "wsdl/Grunndata.wsdl", "$wsdlDir/Grunndata.wsdl")
    )
}

xjc {
    xsdDir.set(layout.projectDirectory.dir("src/main/resources/xsd"))
    xsdFiles = project.files(
        xsdDir.file("HentRoller.xsd")
    )

    outputJavaDir.set(layout.projectDirectory.dir("$buildDir/generated-sources/xsd2java"))
    outputResourcesDir.set(layout.projectDirectory.dir("target/generated-sources/resources"))
}

configure<SourceSetContainer> {
    named("main") {
        println(buildDir)
        java.srcDir("src/main/java")
        java.srcDir("$buildDir/generated-sources/xsd2java")
        java.srcDir("$buildDir/generated-sources/wsdl2java")
    }
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

    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$apacheCxfVersion")
    implementation("org.apache.cxf:cxf-rt-transports-http:$apacheCxfVersion")
    implementation("com.sun.activation:jakarta.activation:$jakartaActivationVersion")


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

task("enforceJavaVersion") {
    val requiredVersion: JavaVersion = JavaVersion.VERSION_12
    if (JavaVersion.current() != requiredVersion) {
        throw IllegalStateException("ERROR: Java $requiredVersion required. Must use specific version to ensure deterministic build (to get same result locally and remote).")
    }
}

tasks.compileJava {
    dependsOn("enforceJavaVersion")
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

    /*fun hexDump(fn: String) {
        val bytes: ByteArray = FileInputStream(File(fn)).use { fis -> fis.readAllBytes() }
        for (i in 0..bytes.size - 1) {
            bytes[i].let { theByte ->
                if (i.rem(48) == 0) println(" :$i")
                if (theByte in 32..125) print(" " + theByte.toChar() + " ") else print(String.format("%02x ", bytes[i]))
            }
        }
    }*/

    fun makeCanonicalZip(source: File, destination: File) {
        val epoch = FileTime.fromMillis(0)
        fun Long.safeToInt(): Int =
            if (this > Int.MAX_VALUE.toLong()) throw IllegalArgumentException("Long too big for Int") else this.toInt()

        val entries = mutableListOf<Pair<ZipEntry, ByteArray>>()

        ZipInputStream(source.inputStream()).use { zipInputStream ->
            var z = zipInputStream.nextEntry
            while (z != null) {
                val bytes = zipInputStream.readBytes()
                val converted = z.apply {
                    setCreationTime(epoch)
                    setLastAccessTime(epoch)
                    setLastModifiedTime(epoch)
                }
                entries.add(converted to bytes)
                z = zipInputStream.nextEntry
            }
        }

        entries.sortBy { it.first.name }

        ZipOutputStream(destination.outputStream()).use { o ->
            entries.forEach {
                o.putNextEntry(it.first)
                o.write(it.second)
            }
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
        val pathToAppJar = Paths.get(libDir, "app.jar")
        val appJar = File(pathStringToAppJar)
        val app2Jar = File(pathStringToApp2Jar)

        makeCanonicalZip(source = appJar, destination = app2Jar)

        Files.delete(pathToAppJar)
        File(pathStringToApp2Jar).renameTo(File(pathStringToAppJar))
        setFileTimeToEpoch(pathToAppJar)

        //hexDump(pathStringToAppJar)
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
