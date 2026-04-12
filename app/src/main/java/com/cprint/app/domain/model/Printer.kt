package com.cprint.app.domain.model

import java.util.Date
import java.util.UUID

/**
 * Domain model representing a printer
 */
data class Printer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val manufacturer: String,
    val model: String,
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String? = null,
    val protocol: String,
    val supportedPaperSizes: List<String> = emptyList(),
    val supportsColor: Boolean = false,
    val supportsDuplex: Boolean = false,
    val maxResolution: String = "600x600",
    val status: PrinterStatus = PrinterStatus.DISCONNECTED,
    val isDefault: Boolean = false,
    val lastConnectedAt: Date? = null,
    val connectionCount: Int = 0
) {
    /**
     * Get display name for the printer
     */
    fun getDisplayName(): String = "$manufacturer $model"

    /**
     * Check if printer is ready for printing
     */
    fun isReady(): Boolean = status == PrinterStatus.READY

    /**
     * Check if printer supports a specific paper size
     */
    fun supportsPaperSize(paperSize: String): Boolean =
        supportedPaperSizes.contains(paperSize) || supportedPaperSizes.isEmpty()
}

/**
 * Printer connection status
 */
enum class PrinterStatus {
    DISCONNECTED,
    CONNECTING,
    READY,
    BUSY,
    ERROR,
    OFFLINE
}

/**
 * Printer protocol types
 */
enum class PrinterProtocol(val value: String) {
    PCL("PCL"),
    ESC_P("ESC/P"),
    POSTSCRIPT("PostScript"),
    GDI("GDI"),
    PDF("PDF"),
    IPP("IPP")
}

/**
 * Known printer vendors with their VID/PID mappings
 */
object KnownPrinters {
    data class PrinterInfo(
        val vendorId: Int,
        val productId: Int,
        val manufacturer: String,
        val model: String,
        val protocol: String
    )

    val KNOWN_PRINTERS = listOf(
        // HP
        PrinterInfo(0x03F0, 0x0000, "HP", "LaserJet", "PCL"),
        PrinterInfo(0x03F0, 0x2B17, "HP", "LaserJet Pro M404", "PCL"),
        PrinterInfo(0x03F0, 0xEA17, "HP", "LaserJet Pro MFP M428", "PCL"),
        PrinterInfo(0x03F0, 0xC517, "HP", "DeskJet 2700", "PCL"),

        // Epson - L3110/L3118 Series (PID 0x113A)
        PrinterInfo(0x04B8, 0x113A, "Epson", "L3110/L3118 Series", "ESC/P-R"),
        // Epson - Other models
        PrinterInfo(0x04B8, 0x08A9, "Epson", "L3150/L4150", "ESC/P"),
        PrinterInfo(0x04B8, 0x08B0, "Epson", "L4160", "ESC/P"),
        PrinterInfo(0x04B8, 0x1139, "Epson", "ET-2710", "ESC/P"),
        // Fallback for other Epson printers
        PrinterInfo(0x04B8, 0x0000, "Epson", "InkJet", "ESC/P"),

        // Canon
        PrinterInfo(0x04A9, 0x0000, "Canon", "Printer", "GDI"),
        PrinterInfo(0x04A9, 0x179C, "Canon", "PIXMA G3020", "GDI"),
        PrinterInfo(0x04A9, 0x179D, "Canon", "PIXMA G3060", "GDI"),
        PrinterInfo(0x04A9, 0x1900, "Canon", "imageCLASS MF264", "PCL"),

        // Brother
        PrinterInfo(0x04F9, 0x0000, "Brother", "Printer", "PCL"),
        PrinterInfo(0x04F9, 0x032C, "Brother", "HL-L2350DW", "PCL"),
        PrinterInfo(0x04F9, 0x032D, "Brother", "DCP-L2550DW", "PCL"),
        PrinterInfo(0x04F9, 0x03DA, "Brother", "MFC-J995DW", "PCL"),

        // Samsung (now HP)
        PrinterInfo(0x04E8, 0x0000, "Samsung", "Printer", "PCL"),
        PrinterInfo(0x04E8, 0x330F, "Samsung", "Xpress M2020", "PCL"),
        PrinterInfo(0x04E8, 0x3310, "Samsung", "Xpress M2070", "PCL"),

        // Xerox
        PrinterInfo(0x0924, 0x0000, "Xerox", "Printer", "PCL"),
        PrinterInfo(0x0924, 0x3E17, "Xerox", "Phaser 3020", "PCL"),

        // Lexmark
        PrinterInfo(0x043D, 0x0000, "Lexmark", "Printer", "PCL"),
        PrinterInfo(0x043D, 0x00C0, "Lexmark", "B2236dw", "PCL")
    )

    /**
     * Find printer info by VID/PID
     */
    fun findPrinter(vendorId: Int, productId: Int): PrinterInfo? {
        return KNOWN_PRINTERS.find { it.vendorId == vendorId && it.productId == productId }
            ?: KNOWN_PRINTERS.find { it.vendorId == vendorId && it.productId == 0x0000 }
    }
}
