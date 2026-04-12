package com.cprint.app.data.local

import androidx.room.*
import com.cprint.app.data.model.entity.PrinterEntity
import com.cprint.app.domain.model.PrinterStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO for printer operations
 */
@Dao
interface PrinterDao {

    @Query("SELECT * FROM printers ORDER BY lastConnectedAt DESC")
    fun getAllPrinters(): Flow<List<PrinterEntity>>

    @Query("SELECT * FROM printers WHERE isDefault = 1 LIMIT 1")
    fun getDefaultPrinter(): Flow<PrinterEntity?>

    @Query("SELECT * FROM printers WHERE status = 'READY' LIMIT 1")
    fun getConnectedPrinter(): Flow<PrinterEntity?>

    @Query("SELECT * FROM printers WHERE status = 'READY' LIMIT 1")
    fun getConnectedPrinterSync(): PrinterEntity?

    @Query("SELECT * FROM printers WHERE id = :printerId")
    suspend fun getPrinterById(printerId: String): PrinterEntity?

    @Query("SELECT * FROM printers WHERE vendorId = :vendorId AND productId = :productId LIMIT 1")
    suspend fun getPrinterByVidPid(vendorId: Int, productId: Int): PrinterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrinter(printer: PrinterEntity)

    @Update
    suspend fun updatePrinter(printer: PrinterEntity)

    @Query("UPDATE printers SET status = :status WHERE id = :printerId")
    suspend fun updatePrinterStatus(printerId: String, status: PrinterStatus)

    @Query("UPDATE printers SET isDefault = 0")
    suspend fun clearDefaultPrinter()

    @Query("UPDATE printers SET isDefault = 1 WHERE id = :printerId")
    suspend fun setDefaultPrinter(printerId: String)

    @Delete
    suspend fun deletePrinter(printer: PrinterEntity)

    @Query("DELETE FROM printers WHERE id = :printerId")
    suspend fun deletePrinterById(printerId: String)
}
