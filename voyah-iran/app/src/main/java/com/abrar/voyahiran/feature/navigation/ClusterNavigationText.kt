package com.abrar.voyahiran.feature.navigation

data class ClusterNavigationFrame(val text: String, val icon: Int = 0, val meters: Int = -1)

/** Experimental OEM icon IDs based on the SDK enum; hardware verification is required. */
object ClusterNavigationText {
    const val MAX_AGE_MS = 15_000L
    fun visibleFrame(road: String?, distance: String?, direction: String?): ClusterNavigationFrame? {
        val street = road?.let(::clean)?.takeIf { it.isNotBlank() } ?: return null
        val remaining = distance?.let(::clean)?.takeIf { it.isNotBlank() } ?: return null
        val instruction = direction?.let(::clean).orEmpty()
        val icon = when {
            "دوربرگردان" in instruction || "دور بزن" in instruction -> "uturn"
            "چپ" in instruction && "راست" !in instruction -> "left"
            "راست" in instruction && "چپ" !in instruction -> "right"
            "مستقیم" in instruction -> "straight"
            else -> null
        }
        // Accessibility cannot expose a drawable resource. If the icon has no spoken
        // description, publish the real road/distance as text instead of inventing a turn.
        return frame(limitUtf8("$remaining - $street", 180), icon)
    }
    fun frame(text: String, resourceName: String?): ClusterNavigationFrame {
        val icon = when (resourceName?.removePrefix("common_ic_")?.removePrefix("routing_module_ic_")) {
            "straight" -> 1
            "left" -> 2
            "right" -> 3
            "left_slight", "left_unsharp" -> 4
            "right_slight", "right_unsharp" -> 5
            "uturn" -> 6
            "right_sharp" -> 7
            "left_sharp" -> 8
            else -> 0
        }
        val normalized = text.map { c -> when(c) {
            in '۰'..'۹' -> '0' + (c - '۰')
            in '٠'..'٩' -> '0' + (c - '٠')
            '٫' -> '.'
            else -> c
        } }.joinToString("")
        val distance = Regex("^([0-9]+(?:[.][0-9]+)?)\\s*(کیلومتر|متر|km|m)(?=\\s|[-–]|$)", RegexOption.IGNORE_CASE).find(normalized)
        val factor = if (distance?.groupValues?.get(2)?.lowercase() in listOf("کیلومتر", "km")) 1000 else 1
        val meters = distance?.groupValues?.get(1)?.toDoubleOrNull()?.times(factor)?.toInt()?.takeIf { it in 0..1_000_000 } ?: -1
        val road = if (distance != null) text.drop(distance.value.length).trim(' ', '-', '–') else text
        return if (icon != 0 && meters >= 0 && road.isNotBlank()) ClusterNavigationFrame(road, icon, meters)
            else ClusterNavigationFrame(text)
    }
    fun parse(id: Int, category: String?, title: String?, text: String?): String? {
        if (id == 504311 || (id != 1874524 && category != "navigation")) return null
        val parts = listOfNotNull(title, text).map(::clean).filter { it.isNotEmpty() }.distinct()
        if (parts.isEmpty()) return null
        return limitUtf8(parts.joinToString(" - "), 180)
    }
    fun clean(value: String) = value.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
        .replace(Regex("\\s+"), " ").trim()
    fun limitUtf8(value: String, limit: Int): String {
        val result = StringBuilder()
        var bytes = 0
        val iterator = value.codePoints().iterator()
        while (iterator.hasNext()) {
            val cp = iterator.nextInt()
            val segment = String(Character.toChars(cp))
            val size = segment.toByteArray(Charsets.UTF_8).size
            if (bytes + size > limit) break
            result.append(segment); bytes += size
        }
        return result.toString()
    }
}

internal class NavigationFreshness {
    private var value: ClusterNavigationFrame? = null
    private var receivedAt = 0L
    private var key: String? = null
    fun update(key: String, text: ClusterNavigationFrame, now: Long, initialAge: Long = 0) {
        this.key = key; value = text; receivedAt = now - initialAge.coerceAtLeast(0)
    }
    fun remove(key: String) { if (this.key == key) clear() }
    fun clear() { value = null; key = null }
    fun current(now: Long): ClusterNavigationFrame? = value?.takeIf { now - receivedAt in 0..ClusterNavigationText.MAX_AGE_MS }
}
