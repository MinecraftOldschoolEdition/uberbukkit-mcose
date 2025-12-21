plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(8)
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.maven.apache.org/maven2/") }
}

dependencies {
    // Compile against uberbukkit
    compileOnly(project(":"))
    
    // LuckPerms dependencies (Java 8 compatible versions)
    implementation("com.google.code.gson:gson:2.7")
    implementation("com.google.guava:guava:19.0")
    
    // Caffeine 2.x for Java 8
    implementation("com.github.ben-manes.caffeine:caffeine:2.9.3")
    
    // Adventure text for messages
    implementation("net.kyori:adventure-api:4.14.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.14.0")
    implementation("net.kyori:adventure-text-serializer-plain:4.14.0")
    implementation("net.kyori:adventure-text-minimessage:4.14.0")
    
    // Event bus
    implementation("net.kyori:event-api:3.0.0")
    
    // OkHttp for web requests
    implementation("com.squareup.okhttp3:okhttp:3.14.9")
    implementation("com.squareup.okio:okio:1.17.6")
    
    // ByteBuddy for runtime class generation
    implementation("net.bytebuddy:byte-buddy:1.12.23")
    
    // Configurate for config files
    implementation("org.spongepowered:configurate-core:3.7.3")
    implementation("org.spongepowered:configurate-yaml:3.7.3")
    implementation("org.spongepowered:configurate-gson:3.7.3")
    implementation("org.spongepowered:configurate-hocon:3.7.3")
    
    // Database drivers (optional, compile only)
    compileOnly("com.zaxxer:HikariCP:4.0.3")
    compileOnly("com.h2database:h2:1.4.200")
    
    // Annotations
    compileOnly("org.checkerframework:checker-qual:3.12.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

version = "1.0"

tasks.shadowJar {
    archiveBaseName.set("LuckPermsOldschool")
    archiveClassifier.set("")
    
    // Relocate dependencies to avoid conflicts
    relocate("com.google.gson", "me.lucko.luckperms.lib.gson")
    relocate("com.google.common", "me.lucko.luckperms.lib.guava")
    relocate("com.github.benmanes.caffeine", "me.lucko.luckperms.lib.caffeine")
    relocate("net.kyori.adventure", "me.lucko.luckperms.lib.adventure")
    relocate("net.kyori.event", "me.lucko.luckperms.lib.eventbus")
    relocate("okio", "me.lucko.luckperms.lib.okio")
    relocate("okhttp3", "me.lucko.luckperms.lib.okhttp3")
    relocate("net.bytebuddy", "me.lucko.luckperms.lib.bytebuddy")
    relocate("ninja.leaping.configurate", "me.lucko.luckperms.lib.configurate")
    
    dependencies {
        exclude(dependency("org.checkerframework:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

