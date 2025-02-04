package io.github.dueris.kotlin.eclipse.gradle

enum class MinecraftVersion constructor(val version: String, val major: Int, val minor: Int) {
    MC1_21_1("1.21-R0.1-SNAPSHOT", 21, 0),
    MC1_21_3("1.21.3-R0.1-SNAPSHOT", 21, 3),
    MC1_21_4("1.21.4-R0.1-SNAPSHOT", 21, 4);

    companion object {
        fun getFromString(version: String): MinecraftVersion {
            for (value in values()) {
                if (value.version == version) {
                    return value;
                }
            }
            throw RuntimeException("Unable to locate version from string!")
        }
    }
}
