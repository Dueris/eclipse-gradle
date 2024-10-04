import io.github.dueris.kotlin.eclipse.gradle.minecraft.MinecraftVersion

plugins {
    java
    id("io.github.dueris.eclipse.gradle") version "1.1.0-beta"
}

eclipse {
    minecraft.set(MinecraftVersion.MC1_21_1)
    widenerPaths.set(files("test.accessWidener"))
}
