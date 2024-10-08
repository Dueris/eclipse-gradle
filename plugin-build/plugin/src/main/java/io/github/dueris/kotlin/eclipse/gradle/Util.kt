package io.github.dueris.kotlin.eclipse.gradle

@Suppress("UNCHECKED_CAST")
class Util {
    fun <T> uncheckedCast(obj: Any?): T =
        obj as T

    fun excludeMapFor(group: String?, module: String?): Map<String, String> =
        mapOfNonNullValuesOf(
            "group" to group,
            "module" to module
        )


    private
    fun mapOfNonNullValuesOf(vararg entries: Pair<String, String?>): Map<String, String> =
        mutableMapOf<String, String>().apply {
            for ((k, v) in entries) {
                if (v != null) {
                    put(k, v)
                }
            }
        }
}
