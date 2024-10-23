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
        println(":found $c wideners to apply, applying...")
        return widener
    }

    private fun operateWideners(
        project: Project,
        eclipseExtension: EclipseExtension,
        widener: AccessWidener
    ) {
        val cloneOf: Set<Configuration> = project.configurations.toSet()
        val input: Pair<Configuration, File> = cloneOf.asSequence()
            .filter { c -> c.isCanBeResolved && c.isCanBeConsumed }
            .flatMap { c -> c.resolve().map { jar -> c to jar } }
            .filter { (_, jar) -> jar.extension == "jar" }
            .filter { (_, jar) -> jar.absolutePath.contains("userdev-" + eclipseExtension.minecraft.get().stringed) }
            .toSet().first()

        val targets = widener.targets.map { it.replace('.', '/') + ".class" }.toSet()
        println(":running access transformer on ${input.second.nameWithoutExtension}...")

        val inputJar = JarInputStream(input.second.inputStream())
        val outputFile = File(
            project.rootDir.toPath().resolve("build").resolve("eclipse-cache").resolve("eclipse-widened-userdev.jar")
                .toAbsolutePath().toString()
        )

        try {
            if (!outputFile.exists()) {
                outputFile.parentFile.mkdirs()
                outputFile.createNewFile()
            } else if (filesAreIdentical(input.second, outputFile)) {
                println(":found cache to be identical, ignoring...")
                return
            }

            widen(outputFile, inputJar, targets, widener)
            if (!filesAreIdentical(outputFile, input.second)) {
                println(":replacing paperweight configuration jar...")
                replaceFile(outputFile, input.second)
                println(":finished applying eclipse workspace to environment")
            }
        } catch (throwed: Throwable) {
            error("An unexpected error occurred when building workspace, skipping..")
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
                while (dis.read(buffer) != -1) { /* DigestInputStream updates the digest automatically */
                }
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

    private fun replaceFile(source: File, target: File) {
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun transformClass(widener: AccessWidener, bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(0)
        val visitor = AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, writer, widener)
        reader.accept(visitor, 0)
        return writer.toByteArray()
    }

}
