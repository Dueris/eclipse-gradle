package io.github.dueris.kotlin.eclipse.gradle

import io.github.dueris.kotlin.eclipse.gradle.provider.ConfigurationProvider
import io.github.dueris.kotlin.eclipse.gradle.provider.DependencyProvider
import io.github.dueris.kotlin.eclipse.gradle.provider.RepositoryProvider
import net.fabricmc.accesswidener.AccessWidener
import net.fabricmc.accesswidener.AccessWidenerClassVisitor
import net.fabricmc.accesswidener.AccessWidenerReader
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.dsl.ArtifactFile
import org.gradle.internal.cc.base.logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

const val EXTENSION_NAME = "eclipse"

@Suppress("UnnecessaryAbstractClass")
abstract class EclipsePlugin : Plugin<Project> {

    companion object {
        var EXTENSION: EclipseExtension? = null
    }

    override fun apply(project: Project) {
        val eclipseExtension = project.extensions.create(EXTENSION_NAME, EclipseExtension::class.java, project)
        EXTENSION = eclipseExtension
        RepositoryProvider(project).apply { project.uri(it) }
        ConfigurationProvider(project).apply()
        DependencyProvider(project).apply()

        eclipseExtension.wideners.convention(project.files())

        project.afterEvaluate {
            operateWideners(
                project, eclipseExtension,
                configureWideners(eclipseExtension, project)
            )
        }
    }

    private fun configureWideners(
        eclipseExtension: EclipseExtension,
        project: Project
    ): AccessWidener {
        val widener = AccessWidener()
        val reader = AccessWidenerReader(widener)
        val verification = Verification()
        var c = 0
        for (file in eclipseExtension.wideners.get()) {
            var resolvedFile: File? = null
            val currentOperating: Project = project.rootProject

            for (subproject in currentOperating.allprojects) {
                val resourceDirs = listOf(
                    File(subproject.projectDir, "src/main/resources")
                )

                resourceDirs.forEach { resourceDir ->
                    if (resourceDir.exists()) {
                        val candidateFile = File(resourceDir, file.name)
                        if (candidateFile.exists()) {
                            resolvedFile = candidateFile
                            return@forEach
                        }
                    }
                }
            }

            resolvedFile?.let { locatedFile ->
                val widenerContents = locatedFile.readText()
                verification.read(widenerContents.toByteArray())
                reader.read(widenerContents.toByteArray())
                c++
            } ?: throw IllegalStateException("Widener file '${file.name}' not found in resources directories.")

        }
        return widener
    }

    private fun operateWideners(
        project: Project,
        eclipseExtension: EclipseExtension,
        widener: AccessWidener
    ) {
        // mojangMappedServer modification
        var rootProj: Project = project.rootProject
        println("Found root project: $rootProj")
        // Old paperweight check first
        val cloneOf: Set<Configuration> = rootProj.configurations.toSet()
        var setInput: Set<Pair<Configuration, File>> = cloneOf.asSequence()
            .filter { c -> c.isCanBeResolved && c.isCanBeConsumed }
            .flatMap { c ->
                c.resolve().map { jar -> c to jar }
            }
            .filter { (_, jar) ->
                jar.extension == "jar"
            }
            .filter { (_, jar) -> jar.absolutePath.contains("userdev-" + eclipseExtension.minecraft.get()) }
            .toSet()
        if (setInput.isEmpty()) {
            val version: MinecraftVersion = MinecraftVersion.getFromString(eclipseExtension.minecraft.get())
            if (version == MinecraftVersion.MC1_21_4 || version.major > 21 || (version.major == 21 && version.minor >= 4)) {
                // Using paperweight 2.x.x
                println("Detected paperweight 2.x.x, running through new source search")
                setInput = cloneOf.asSequence()
                    .filter { c -> c.isCanBeResolved && c.isCanBeConsumed }
                    .flatMap { c ->
                        c.resolve().map { jar -> c to jar }
                    }
                    .filter { (_, jar) ->
                        jar.extension == "jar" && jar.nameWithoutExtension == "patchedSources"
                    }
                    .toSet()
            } else {
                println("Unable to locate paperweight jar, exiting widener")
                return
            }
        }
        if (setInput.isEmpty()) {
            println("Unable to locate paperweight jar, exiting widener")
            return
        }
        val input: Pair<Configuration, File> = setInput.first()
        //C:\Users\jedim\.gradle\caches\
        // paperweight-userdev\613081b689735a1c6be92857491be1a4773c6b9c71a65baf922ee4f718c54ed2\module\io.papermc.paper\dev-bundle\1.21.4-R0.1-SNAPSHOT\paperweight\setupCache
        val d = DependencyProvider(project);
        for (file in input.first.files) {
            if (file.absolutePath == input.second.absolutePath) {
                continue
            } else {
                d.addFile(file.absolutePath)
            }
        }

        val targets = widener.targets.map { it.replace('.', '/') + ".class" }.toSet()

        val inputJar = JarInputStream(input.second.inputStream())
        val outputFile = File(
            project.rootDir.toPath().resolve("build").resolve("eclipse-cache").resolve("eclipse-widened-userdev.jar")
                .toAbsolutePath().toString()
        )

        processMojmapServer(project, input, outputFile, inputJar, targets, widener)

        /* -- From paperweight userdev
            depList += MavenArtifact(
                "com.google.code.findbugs",
                "jsr305",
                "3.0.2"
            )
            depList += MavenArtifact(
                "org.apache.logging.log4j",
                "log4j-api",
                "2.17.0"
            )
            depList += MavenArtifact(
                "org.jetbrains",
                "annotations",
                "23.0.0"
            )
             */
        d.addFile(outputFile.absolutePath)
        d.compileOnly("com.google.code.findbugs:jsr305:3.0.2")
        d.compileOnly("org.apache.logging.log4j:log4j-api:2.17.0")
        d.compileOnly("org.jetbrains:annotations:23.0.0")
    }

    private fun processMojmapServer(
        project: Project,
        input: Pair<Configuration, File>,
        outputFile: File,
        inputJar: JarInputStream,
        targets: Set<String>,
        widener: AccessWidener
    ) {
        project.tasks.register("widenEclipseServer") {
            doLast {
                println(":running access transformer on ${input.second.nameWithoutExtension}...")
                if (project.configurations.contains(input.first)) {
                    project.configurations.remove(input.first)
                    if (!outputFile.exists()) {
                        outputFile.parentFile.mkdirs()
                        outputFile.createNewFile()
                    }

                    widen(outputFile, inputJar, targets, widener)
                }
            }
        }
        if (!outputFile.exists()) {
            outputFile.parentFile.mkdirs()
            outputFile.createNewFile()
            widen(outputFile, inputJar, targets, widener)
        }
    }

    private fun filesAreIdentical(file1: File, file2: File): Boolean {
        if (file1.length() != file2.length()) {
            return false
        }

        val digest1 = fileToDigest(file1)
        val digest2 = fileToDigest(file2)

        return digest1.contentEquals(digest2)
    }

    private fun fileToDigest(file: File): ByteArray {
        val buffer = ByteArray(1024)
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            DigestInputStream(fis, md).use { dis ->
                while (dis.read(buffer) != -1) { /* DigestInputStream updates the digest automatically */ }
            }
        }
        return md.digest()
    }

    private fun widen(
        outputFile: File,
        inputJar: JarInputStream,
        targets: Set<String>,
        widener: AccessWidener
    ) {
        val outputJar = JarOutputStream(outputFile.outputStream())

        try {
            var entry = inputJar.nextEntry
            while (entry != null) {
                outputJar.putNextEntry(ZipEntry(entry.name))

                if (targets.contains(entry.name)) {
                    logger.debug("Widening entry '{}'", entry.name)
                    outputJar.write(transformClass(widener, inputJar.readBytes()))
                } else {
                    inputJar.copyTo(outputJar)
                }

                entry = inputJar.nextEntry
            }
        } finally {
            inputJar.close()
            outputJar.close()
        }
    }

    private fun transformClass(widener: AccessWidener, bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(0)
        val visitor = AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, writer, widener)
        reader.accept(visitor, 0)
        return writer.toByteArray()
    }

}
