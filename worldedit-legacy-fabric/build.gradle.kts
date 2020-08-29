import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.loom.task.RemapJarTask
import java.util.function.Function

buildscript {
    repositories {
        mavenCentral()
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
    }
    dependencies {
        classpath("net.fabricmc:fabric-loom:0.4.3")
    }
}

applyPlatformAndCoreConfiguration()
applyShadowConfiguration()

apply(plugin = "fabric-loom")
apply(plugin = "java-library")

configure<LoomGradleExtension> {
//    accessWidener = file("src/main/resources/worldedit.accesswidener")

    intermediaryUrl = Function {
        "https://dl.bintray.com/legacy-fabric/Legacy-Fabric-Maven/net/fabricmc/intermediary/$version/intermediary-$version-v2.jar"
    }
}

val minecraftVersion = "1.8.9"
val yarnMappings = "1.8.9+build.202008241659:v2"
val loaderVersion = "0.8.7+build.202006122116"

configurations.all {
    resolutionStrategy {
        force("com.google.guava:guava:21.0")
    }
}

repositories {
    maven {
        name = "Legacy-Fabric"
        url = uri("https://dl.bintray.com/legacy-fabric/Legacy-Fabric-Maven/")
    }

    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
}

dependencies {
    "api"(project(":worldedit-core"))
    "implementation"("org.apache.logging.log4j:log4j-slf4j-impl:2.8.1")

    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    "mappings"("net.fabricmc:yarn:$yarnMappings")
    "modImplementation"("net.fabricmc:fabric-loader-1.8.9:$loaderVersion")

    // Declare fabric-api dependency
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:0.7.0")

    // Hook these up manually, because Fabric doesn't seem to quite do it properly.
    "compileOnly"("net.fabricmc:sponge-mixin:${project.versions.mixin}")
    "annotationProcessor"("net.fabricmc:sponge-mixin:${project.versions.mixin}")
    "annotationProcessor"("net.fabricmc:fabric-loom:${project.versions.loom}")
}

configure<BasePluginConvention> {
    archivesBaseName = "$archivesBaseName-mc$minecraftVersion"
}

tasks.named<Copy>("processResources") {
    // this will ensure that this task is redone when the versions change.
    inputs.property("version", project.ext["internalVersion"])

    from(sourceSets["main"].resources.srcDirs) {
        include("fabric.mod.json")
        expand("version" to project.ext["internalVersion"])
    }

    // copy everything else except the mod json
    from(sourceSets["main"].resources.srcDirs) {
        exclude("fabric.mod.json")
    }
}

addJarManifest(includeClasspath = true)

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("dist-dev")
    dependencies {
        relocate("org.slf4j", "com.sk89q.worldedit.slf4j")
        relocate("org.apache.logging.slf4j", "com.sk89q.worldedit.log4jbridge")
        relocate("org.antlr.v4", "com.sk89q.worldedit.antlr4")

        include(dependency("org.slf4j:slf4j-api"))
        include(dependency("org.apache.logging.log4j:log4j-slf4j-impl"))
        include(dependency("org.antlr:antlr4-runtime"))
    }
}

tasks.register<Jar>("deobfJar") {
    from(sourceSets["main"].output)
    archiveClassifier.set("dev")
}

artifacts {
    add("archives", tasks.named("deobfJar"))
}

tasks.register<RemapJarTask>("remapShadowJar") {
    val shadowJar = tasks.getByName<ShadowJar>("shadowJar")
    dependsOn(shadowJar)
    input.set(shadowJar.archiveFile)
    archiveFileName.set(shadowJar.archiveFileName.get().replace(Regex("-dev\\.jar$"), ".jar"))
    addNestedDependencies.set(true)
}

tasks.named("assemble").configure {
    dependsOn("remapShadowJar")
}
