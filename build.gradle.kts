plugins {
    java
    `java-library`
    `maven-publish`
    id("io.freefair.lombok") version "6.5.1"
    antlr
}

group = "hu.nemaberci"
version = "1.0.2"

repositories {
    mavenCentral()
    maven() {
        url = uri("https://maven.pkg.github.com/nemaberci/regex_parser_api")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        }
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    api("hu.nemaberci:regex-api:1.0")
    implementation("com.thoughtworks.qdox:qdox:2.0.2")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("com.squareup:javapoet:1.13.0")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/nemaberci/regex_parser_generator")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}