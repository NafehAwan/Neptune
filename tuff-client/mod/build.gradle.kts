import java.net.URI

plugins {
    // Applies the right Loom for the Minecraft version
    id("dev.kikugie.loom-back-compat")
}

// One version for all of Glade: the launcher's, so each release carries a
// matching launcher and mod (the launcher's updater compares these).
val gladeVersion: String = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
    .find(rootProject.file("../launcher/src-tauri/tauri.conf.json").readText())!!.groupValues[1]
val mc: String = sc.current.version
val mcCompat: String = property("mod.mc_compat") as String

version = "$gladeVersion+$mc"
base.archivesName = "tuff-client"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    sc.current.parsed >= "1.17" -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}

/** The newest Fabric API build for a Minecraft version, from Fabric's maven. */
fun latestFabricApi(mc: String): String {
    val xml = URI("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml").toURL().readText()
    return Regex("<version>([^<]+\\+${Regex.escape(mc)})</version>").findAll(xml).map { it.groupValues[1] }.lastOrNull()
        ?: error("No Fabric API build for Minecraft $mc")
}

// Files written for some Minecraft versions only start with a line like
// "// glade: minecraft >=1.21.6 <26.1"; other versions leave them out.
fun inRange(range: String): Boolean = range.trim().split(Regex("\\s+")).all { t ->
    when {
        t.startsWith(">=") -> sc.current.parsed >= t.substring(2)
        t.startsWith("<=") -> sc.current.parsed <= t.substring(2)
        t.startsWith(">") -> sc.current.parsed > t.substring(1)
        t.startsWith("<") -> sc.current.parsed < t.substring(1)
        else -> mc == t
    }
}
val javaRoot = rootProject.file("src/main/java")
val versionedOut: List<String> = javaRoot.walkTopDown()
    .filter { it.isFile && it.name.endsWith(".java") }
    .mapNotNull { f ->
        val header = f.useLines { lines -> lines.take(2).firstOrNull { it.startsWith("// glade: minecraft ") } }
        header?.removePrefix("// glade: minecraft ")?.takeIf { !inRange(it) }?.let { f.relativeTo(javaRoot).invariantSeparatorsPath }
    }
    .toList()
sourceSets.named("main") {
    java { versionedOut.forEach { exclude(it) } }
}

val fapiVersion: String = (findProperty("deps.fabric_api") as String?) ?: latestFabricApi(mc)

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    // Mojang's names on versions before 26.1
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fapiVersion")
}

java {
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf(
        "version" to gladeVersion,
        "minecraft" to mcCompat,
        "java" to requiredJava.majorVersion,
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }

    // Each version lists only the mixins that exist for it: a mixin that doesn't
    // apply to a version is commented out there and compiles to nothing.
    dependsOn(tasks.compileJava)
    val mixinClasses = layout.buildDirectory.dir("classes/java/main/net/tuffclient/mixin").get().asFile
    val level = "JAVA_${requiredJava.majorVersion}"
    filesMatching("tuff.mixins.json") {
        filter { line ->
            when {
                line.contains("\"@MIXINS@\"") -> (mixinClasses.listFiles() ?: emptyArray())
                    .map { it.name }
                    .filter { it.endsWith(".class") && !it.contains('$') }
                    .map { "\t\t\"${it.removeSuffix(".class")}\"" }
                    .sorted()
                    .joinToString(",\n")
                else -> line.replace("@JAVA@", level)
            }
        }
    }
}

// Client game tests (26.3): boot the real game, open every Glade screen and save
// screenshots so CI checks the mod at runtime.
if (mc == "26.3") {
    fabricApi {
        configureTests {
            createSourceSet = true
            modId = "tuff-test"
            enableGameTests = false
            eula = true
        }
    }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds the mod jar and copies it to build/libs/ at the root"
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

