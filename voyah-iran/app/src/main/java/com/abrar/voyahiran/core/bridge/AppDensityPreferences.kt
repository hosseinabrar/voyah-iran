package com.abrar.voyahiran.core.bridge

internal object AppDensityPreferences {
    const val GLOBAL = "voyah_iran_app_density"
    const val PACKAGES = "voyah_iran_app_density_packages"
    val values = listOf(120, 140, 160, 180, 200)
    fun validPackage(value: String) = value.length <= 255 && value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
    fun parse(raw: String?): Map<String, Int> = buildMap {
        raw.orEmpty().split(';').forEach { entry ->
            val parts = entry.split('=')
            if (parts.size == 2 && validPackage(parts[0])) {
                val dpi = parts[1].toIntOrNull()
                if (dpi in AppDensityPreferences.values) put(parts[0], dpi!!)
            }
        }
    }
    fun encode(values: Map<String, Int>) = values.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }.ifEmpty { "-" }
}
