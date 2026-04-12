package com.cprint.app.domain.model

/**
 * Print settings model
 */
data class PrintSettings(
    val paperSize: PaperSize = PaperSize.A4,
    val orientation: Orientation = Orientation.PORTRAIT,
    val colorMode: ColorMode = ColorMode.COLOR,
    val copies: Int = 1,
    val pageRange: PageRange? = null,
    val duplexMode: DuplexMode = DuplexMode.SINGLE,
    val quality: PrintQuality = PrintQuality.NORMAL,
    val pagesPerSheet: Int = 1,
    val scaleType: ScaleType = ScaleType.FIT_TO_PAGE,
    val marginType: MarginType = MarginType.DEFAULT
) {
    /**
     * Validate settings
     */
    fun isValid(): Boolean {
        return copies > 0 && copies <= 99 &&
                pagesPerSheet in 1..16
    }

    /**
     * Get page range string representation
     */
    fun getPageRangeString(): String? {
        return pageRange?.toString()
    }
}

/**
 * Page range specification
 */
data class PageRange(
    val startPage: Int? = null,
    val endPage: Int? = null,
    val specificPages: List<Int> = emptyList()
) {
    /**
     * Check if a page should be printed
     */
    fun shouldPrintPage(pageNumber: Int, totalPages: Int): Boolean {
        if (specificPages.isNotEmpty()) {
            return specificPages.contains(pageNumber)
        }

        val start = startPage ?: 1
        val end = endPage ?: totalPages
        return pageNumber in start..end
    }

    /**
     * Get total pages to print
     */
    fun getPagesToPrint(totalPages: Int): Int {
        if (specificPages.isNotEmpty()) {
            return specificPages.count { it in 1..totalPages }
        }

        val start = startPage ?: 1
        val end = endPage ?: totalPages
        return (end - start + 1).coerceAtLeast(0)
    }

    override fun toString(): String {
        return when {
            specificPages.isNotEmpty() -> specificPages.joinToString(",")
            startPage != null && endPage != null -> "$startPage-$endPage"
            startPage != null -> "$startPage-"
            endPage != null -> "1-$endPage"
            else -> ""
        }
    }

    companion object {
        /**
         * Parse page range from string
         */
        fun parse(rangeString: String): PageRange? {
            if (rangeString.isBlank()) return null

            val specificPages = mutableListOf<Int>()
            var startPage: Int? = null
            var endPage: Int? = null

            val parts = rangeString.split(",")

            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.contains("-")) {
                    val range = trimmed.split("-")
                    if (range.size == 2) {
                        startPage = range[0].toIntOrNull()
                        endPage = range[1].toIntOrNull()
                    }
                } else {
                    trimmed.toIntOrNull()?.let { specificPages.add(it) }
                }
            }

            return PageRange(startPage, endPage, specificPages)
        }
    }
}

/**
 * Scale type options
 */
enum class ScaleType(val value: String) {
    ACTUAL_SIZE("actual_size"),
    FIT_TO_PAGE("fit_to_page"),
    FIT_TO_PRINTABLE("fit_to_printable"),
    CUSTOM("custom")
}

/**
 * Margin type options
 */
enum class MarginType(val value: String) {
    DEFAULT("default"),
    NONE("none"),
    MINIMUM("minimum"),
    CUSTOM("custom")
}
