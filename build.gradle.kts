plugins {
    kotlin("jvm") version "1.9.25"
    application
}

group = "com.joshuaarnold"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.mysql:mysql-connector-j:8.4.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

application {
    mainClass.set("payrollsync.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
