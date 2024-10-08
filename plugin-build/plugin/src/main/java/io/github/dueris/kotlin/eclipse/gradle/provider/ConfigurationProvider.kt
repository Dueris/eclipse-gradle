package io.github.dueris.kotlin.eclipse.gradle.provider

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import javax.inject.Inject


class ConfigurationProvider @Inject constructor(private val project: Project) {
    private val configuration: ConfigurationContainer = project.configurations

    private fun getConfigurations(): ConfigurationContainer {
        return configuration
    }

    private fun getProject(): Project {
        return project
    }

    fun apply() {

    }

    private fun register(name: String, role: Role): NamedDomainObjectProvider<Configuration> {
        return getConfigurations().register(name, role::apply)
    }

    enum class Role(private val canBeConsumed: Boolean, private val canBeResolved: Boolean) {
        NONE(false, false),
        CONSUMABLE(true, false),
        RESOLVABLE(false, true);

        fun apply(configuration: Configuration) {
            configuration.isCanBeConsumed = canBeConsumed
            configuration.isCanBeResolved = canBeResolved
        }
    }
}
