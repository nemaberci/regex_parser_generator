plugins {
    java
    `java-library`
    `maven-publish`
    id("io.freefair.lombok") version "6.5.1"
    antlr
}

group = "hu.nemaberci"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    api("hu.nemaberci:regex-api:1.0-SNAPSHOT")
    implementation("com.thoughtworks.qdox:qdox:2.0.2")
    antlr("org.antlr:antlr4:4.11.1")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.slf4j:slf4j-api:1.7.25")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

publishing {
    publications {
        // todo: add publications
        create<MavenPublication>("maven") {

            from(components["java"])
        }
    }
}