package com.cprint.app.data.model.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.cprint.app.data.local.Converters
import com.cprint.app.domain.model.Printer
import com.cprint.app.domain.model.PrinterStatus
import java.util.Date

/**
 * Room entity for printer storage
 */
@Entity(tableName = "printers")
@TypeConverters(Converters::class)
data class PrinterEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String?,
    val protocol: String,
    val supportedPaperSizes: List<String>,
    val supportsColor: Boolean,
    val supportsDuplex: Boolean,
    val maxResolution: String,
    val status: PrinterStatus,
    val isDefault: Boolean,
    val lastConnectedAt: Date?,
    val connectionCount: Int
) {
    /**
     * Convert entity to domain model
     */
    fun toDomainModel(): Printer = Printer(
        id = id,
        name = name,
        manufacturer = manufacturer,
        model = model,
        vendorId = vendorId,
        productId = productId,
        serialNumber = serialNumber,
        protocol = protocol,
        supportedPaperSizes = supportedPaperSizes,
        supportsColor = supportsColor,
        supportsDuplex = supportsDuplex,
        maxResolution = maxResolution,
        status = status,
        isDefault = isDefault,
        lastConnectedAt = lastConnectedAt,
        connectionCount = connectionCount
    )

    companion object {
        /**
         * Create entity from domain model
         */
        fun fromDomainModel(printer: Printer): PrinterEntity = PrinterEntity(
            id = printer.id,
            name = printer.name,
            manufacturer = printer.manufacturer,
            model = printer.model,
            vendorId = printer.vendorId,
            productId = printer.productId,
            serialNumber = printer.serialNumber,
            protocol = printer.protocol,
            supportedPaperSizes = printer.supportedPaperSizes,
            supportsColor = printer.supportsColor,
            supportsDuplex = printer.supportsDuplex,
            maxResolution = printer.maxResolution,
            status = printer.status,
            isDefault = printer.isDefault,
            lastConnectedAt = printer.lastConnectedAt,
            connectionCount = printer.connectionCount
        )
    }
}
