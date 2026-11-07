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

    internal fun toPageIndexes(totalPages: Int): List<Int> {
        if (specificPages.isNotEmpty()) {
            require(specificPages.all { it in 1..totalPages }) { "打印页码超出文档范围" }
            return specificPages.distinct().sorted().map { it - 1 }
        }
        val start = startPage ?: 1
        val end = endPage ?: totalPages
        require(start in 1..totalPages && end in start..totalPages) { "打印页码超出文档范围" }
        return (start - 1 until end).toList()
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
        /** Validate a complete selection against the actual PDF. No partial matches. */
        internal fun parseSelection(value: String, totalPages: Int): PageRange? {
            val ranges = value.split(',', '，').map { segment ->
                val parts = segment.split('-', limit = 3).map(String::trim)
                val start = parts[0].toIntOrNull() ?: return null
                val end = when (parts.size) {
                    1 -> start
                    2 -> parts[1].toIntOrNull() ?: return null
                    else -> return null
                }
                if (start !in 1..totalPages || end !in start..totalPages) return null
                start..end
            }
            val single = ranges.singleOrNull()
            return when {
                single == null -> PageRange(specificPages = buildSet {
                    ranges.forEach { addAll(it) }
                }.sorted())
                single.first == single.last -> PageRange(specificPages = listOf(single.first))
                else -> PageRange(startPage = single.first, endPage = single.last)
            }
        }

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
