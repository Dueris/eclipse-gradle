package io.github.dueris.kotlin.eclipse.gradle.access;

import net.fabricmc.accesswidener.AccessWidener
import net.fabricmc.accesswidener.AccessWidenerClassVisitor
import net.fabricmc.accesswidener.AccessWidenerReader
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

abstract class AccessWidenerTransformer : TransformAction<AccessWidenerTransformer.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        val wideners: ListProperty<String>
    }

    override fun transform(outputs: TransformOutputs) {
        val widener = AccessWidener()
        val reader = AccessWidenerReader(widener)
        parameters.wideners.get().forEach {
            reader.read(it.toByteArray())
        }

        val targets = widener.targets.map { it.replace('.', '/') + ".class" }.toSet()
        val input = inputArtifact.get().asFile

        val inputJar = JarInputStream(input.inputStream())
        val outputJar = JarOutputStream(outputs.file("widened-${input.name}").outputStream())

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

    private fun transformClass(widener: AccessWidener, bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(0)
        val visitor = AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, writer, widener)
        reader.accept(visitor, 0)
        return writer.toByteArray()
    }

    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

}
