plugins {
    id("java")
    id("com.gradleup.shadow") version "9.5.1"
    id("maven-publish")
}

group = "rpg"
version = "1.0.15"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    // TEMP DEV LOOP: prefers a locally-published orelia-core (./gradlew publishToMavenLocal
    // in that repo) over jitpack, so in-flight core changes are picked up without a push.
    // Remove this line before merging - production builds should resolve from jitpack only.
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // Resolves orelia-core straight from its GitHub repo - no shared monorepo/artifact
    // registry needed. See https://jitpack.io/#orelia-mc/orelia-core
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Money (deposit/withdraw for quest rewards, NPC shop) goes straight through Vault,
    // the same way any other Vault-integrated plugin would - no custom EconomyApi needed.
    // Excludes VaultAPI's transitive org.bukkit:bukkit:1.13.1, which otherwise conflicts
    // with the org.bukkit:bukkit capability paper-api provides.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // orelia-world only ever calls into orelia-core through rpg.api (published via Bukkit's
    // ServicesManager at runtime) or the generic rpg.core.* infrastructure classes
    // (ModuleManager/ConfigManager/PlayerDataManager/PlayerDataComponent...) - never
    // gameplay-module internals like rpg.status/rpg.item directly.
    compileOnly("com.github.orelia-mc:orelia-core:main-SNAPSHOT")
    // Soft dependency (plugin.yml softdepend) - only rpg.extra.api.PartyApi, used to resolve a
    // dungeon challenger's real party. Null-guarded everywhere; orelia-extra may not be installed.
    compileOnly("com.github.orelia-mc:orelia-extra:main-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("orelia-world")
    }
    build {
        dependsOn(shadowJar)
    }
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    test {
        useJUnitPlatform()
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// Publishes to mavenLocal under the same coordinates jitpack normally resolves
// (com.github.orelia-mc:orelia-world:main-SNAPSHOT), so orelia-extra can pick up local
// changes during development without waiting on a push. Temporary dev-loop aid only -
// production builds still resolve this dependency from jitpack.io.
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.orelia-mc"
            artifactId = "orelia-world"
            version = "main-SNAPSHOT"
            from(components["java"])
        }
    }
}
