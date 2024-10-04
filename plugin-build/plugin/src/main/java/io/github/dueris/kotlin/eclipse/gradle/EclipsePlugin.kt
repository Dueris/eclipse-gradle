package io.github.dueris.kotlin.eclipse.gradle

import io.github.dueris.kotlin.eclipse.gradle.access.AccessWidenerTransformer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute

const val EXTENSION_NAME = "eclipse"

@Suppress("UnnecessaryAbstractClass")
abstract class EclipsePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val eclipseExtension = project.extensions.create(EXTENSION_NAME, EclipseExtension::class.java, project)

        eclipseExtension.widenerPaths.convention(project.files())

        project.afterEvaluate {
            val widened = Attribute.of("widened", Boolean::class.javaObjectType)

            project.dependencies.attributesSchema.attribute(widened)
            project.dependencies.artifactTypes.getByName("jar") {
                it.attributes.attribute(widened, false)
            }
            project.dependencies.registerTransform(AccessWidenerTransformer::class.java) {
                it.from.attribute(widened, false)
                it.to.attribute(widened, true)
                it.parameters.wideners.set(eclipseExtension.widenerPaths.get().map { file -> file.readText() })
            }
            project.configurations.all {
                if (it.isCanBeResolved) {
                    it.attributes.attribute(widened, true)
                }
            }
        }
    }
}
