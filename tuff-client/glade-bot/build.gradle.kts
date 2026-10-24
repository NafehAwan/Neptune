import java.net.URI

plugins {
    id("dev.kikugie.loom-back-compat")
}

val mc: String = sc.current.version
val mcCompat: String = property("mod.mc_compat") as String
version = "${property("mod.version")}+$mc"
base.archivesName = "glade-bot"

/** The newest Fabric API build for a Minecraft version, from Fabric's maven. */
fun latestFabricApi(mc: String): String {
    val xml = URI("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml").toURL().readText()
    return Regex("<version>([^<]+\\+${Regex.escape(mc)})</version>").findAll(xml).map { it.groupValues[1] }.lastOrNull()
        ?: error("No Fabric API build for Minecraft $mc")
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${latestFabricApi(mc)}")
}

java {
    targetCompatibility = JavaVersion.VERSION_21
    sourceCompatibility = JavaVersion.VERSION_21
    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString(), "minecraft" to mcCompat)
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

// The in-game test: start a match against the bot and check it fights.
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "gladebot-test"
        enableGameTests = false
        eula = true
    }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(loomx.modJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs"))
}

// For tools/apidump.py: the jars this version compiles against.
tasks.register("gladeClasspath") {
    notCompatibleWithConfigurationCache("writes the resolved classpath for the API dump")
    val out = layout.buildDirectory.file("glade-classpath.txt")
    doLast {
        out.get().asFile.apply { parentFile.mkdirs() }.writeText(project.configurations.getByName("compileClasspath").files.joinToString("\n"))
    }
}

// The CI self test drives a dedicated server through its console.
tasks.matching { it.name == "runServer" }.configureEach {
    (this as JavaExec).standardInput = System.`in`
}
