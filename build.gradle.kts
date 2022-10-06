plugins {
    java
    `java-library`
    `maven-publish`
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