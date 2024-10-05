package io.github.dueris.kotlin.eclipse.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import javax.inject.Inject

@Suppress("UnnecessaryAbstractClass")
abstract class EclipseExtension
    @Inject
    constructor(project: Project) {
        private val objects = project.objects

        val minecraft: Property<MinecraftVersion> = objects.property(MinecraftVersion::class.java)
        val wideners: Property<FileCollection> = objects.property(FileCollection::class.java)
    }
