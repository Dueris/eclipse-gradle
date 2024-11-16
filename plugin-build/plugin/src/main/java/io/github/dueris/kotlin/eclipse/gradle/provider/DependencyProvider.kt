package io.github.dueris.kotlin.eclipse.gradle.provider

import io.github.dueris.kotlin.eclipse.gradle.Util
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.accessors.runtime.addDependencyTo

class DependencyProvider(private val project: Project) {
    private val dependencies: DependencyHandler = project.dependencies
    private val util: Util = Util()

    fun apply() {
        dependencies.compileOnly("net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7") {
            exclude(group = "com.google.guava")
            exclude(group = "com.google.code.gson")
            exclude(group = "org.ow2.asm")
        }
        dependencies.compileOnly("io.github.llamalad7:mixinextras-common:0.4.1") {
            exclude(group = "org.apache.commons")
        }
    }

    fun addFile(dependencyNotation: String) {
        this.dependencies.compileOnly(project.files(dependencyNotation))
    }

    fun compileOnly(dependencyNotation: String) {
        this.dependencies.compileOnly(dependencyNotation)
    }

    private fun DependencyHandler.implementation(dependencyNotation: Any): Dependency? =
        add("implementation", dependencyNotation)

    private fun DependencyHandler.compileOnly(
        dependencyNotation: String,
        dependencyConfiguration: Action<ExternalModuleDependency>
    ): ExternalModuleDependency = addDependencyTo(
        this, "compileOnly", dependencyNotation, dependencyConfiguration
    )

    private fun DependencyHandler.compileOnly(dependencyNotation: Any): Dependency? =
        add("compileOnly", dependencyNotation)

    private fun <T : ModuleDependency> T.exclude(group: String? = null, module: String? = null): T =
        util.uncheckedCast(exclude(util.excludeMapFor(group, module)))
}
