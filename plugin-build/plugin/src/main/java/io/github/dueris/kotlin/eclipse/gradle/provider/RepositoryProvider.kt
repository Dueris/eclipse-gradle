package io.github.dueris.kotlin.eclipse.gradle.provider

import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.net.URI
import java.util.function.Function


class RepositoryProvider(project: Project) {
    private val repositoryHandler: RepositoryHandler = project.repositories

    fun getRepositories(): RepositoryHandler {
        return repositoryHandler
    }

    fun apply(uriBuilder: Function<String, URI>) {
        repositoryHandler.maven {
            name = "Mojang"
            url = uriBuilder.apply("https://libraries.minecraft.net/")

            artifactUrls(ArtifactRepositoryContainer.MAVEN_CENTRAL_URL)
        }
        repositoryHandler.maven {
            name = "Fabric"
            url = uriBuilder.apply("https://maven.fabricmc.net/")
        }
    }

    fun RepositoryHandler.maven(url: Any) =
        maven { setUrl(url) }
}
