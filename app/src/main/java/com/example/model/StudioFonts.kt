package com.example.model

enum class FontSource {
    BUNDLED, SYSTEM, CUSTOM
}

data class StudioFont(
    val key: String,
    val label: String,
    val source: FontSource,
    val androidFamily: String? = null,
    val bold: Boolean = true
)

object StudioFonts {
    const val CUSTOM_KEY = "custom"

    val catalog: List<StudioFont> = listOf(
        StudioFont("space_grotesk", "Grotesk", FontSource.BUNDLED),
        StudioFont("plus_jakarta_sans", "Jakarta", FontSource.BUNDLED, bold = false),
        StudioFont("jetbrains_mono", "Mono", FontSource.BUNDLED, bold = false),
        StudioFont("oswald", "Oswald", FontSource.SYSTEM, "sans-serif-condensed"),
        StudioFont("bebas", "Bebas", FontSource.SYSTEM, "sans-serif-condensed"),
        StudioFont("roboto_black", "Black", FontSource.SYSTEM, "sans-serif-black"),
        StudioFont("roboto", "Roboto", FontSource.SYSTEM, "sans-serif", bold = false),
        StudioFont("playfair", "Playfair", FontSource.SYSTEM, "serif"),
        StudioFont("noto_serif", "Serif", FontSource.SYSTEM, "serif", bold = false),
        StudioFont("script", "Script", FontSource.SYSTEM, "cursive"),
        StudioFont("condensed", "Condensed", FontSource.SYSTEM, "sans-serif-condensed", bold = false),
        StudioFont("courier", "Courier", FontSource.SYSTEM, "monospace", bold = false)
    )

    fun info(key: String): StudioFont? = catalog.firstOrNull { it.key == key }
}
