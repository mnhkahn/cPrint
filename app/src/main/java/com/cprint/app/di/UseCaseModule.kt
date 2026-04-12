package com.cprint.app.di

import com.cprint.app.domain.repository.DocumentRepository
import com.cprint.app.domain.repository.PrintJobRepository
import com.cprint.app.domain.repository.PrinterRepository
import com.cprint.app.domain.repository.UsbPrintRepository
import com.cprint.app.domain.usecase.document.GetRecentDocumentsUseCase
import com.cprint.app.domain.usecase.document.OpenDocumentUseCase
import com.cprint.app.domain.usecase.print.CancelPrintJobUseCase
import com.cprint.app.domain.usecase.print.CreatePrintJobUseCase
import com.cprint.app.domain.usecase.print.GetPrintJobsUseCase
import com.cprint.app.domain.usecase.print.RetryPrintJobUseCase
import com.cprint.app.domain.usecase.printer.ConnectPrinterUseCase
import com.cprint.app.domain.usecase.printer.DisconnectPrinterUseCase
import com.cprint.app.domain.usecase.printer.GetConnectedPrinterUseCase
import com.cprint.app.domain.usecase.printer.GetPrinterCapabilitiesUseCase
import com.cprint.app.domain.usecase.printer.QueryPrinterStatusUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Use case dependency injection module
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    // Printer Use Cases
    @Provides
    @Singleton
    fun provideConnectPrinterUseCase(
        printerRepository: PrinterRepository
    ): ConnectPrinterUseCase {
        return ConnectPrinterUseCase(printerRepository)
    }

    @Provides
    @Singleton
    fun provideDisconnectPrinterUseCase(
        printerRepository: PrinterRepository
    ): DisconnectPrinterUseCase {
        return DisconnectPrinterUseCase(printerRepository)
    }

    @Provides
    @Singleton
    fun provideGetConnectedPrinterUseCase(
        printerRepository: PrinterRepository
    ): GetConnectedPrinterUseCase {
        return GetConnectedPrinterUseCase(printerRepository)
    }

    @Provides
    @Singleton
    fun provideGetPrinterCapabilitiesUseCase(
        printerRepository: PrinterRepository
    ): GetPrinterCapabilitiesUseCase {
        return GetPrinterCapabilitiesUseCase(printerRepository)
    }

    @Provides
    @Singleton
    fun provideQueryPrinterStatusUseCase(
        printerRepository: PrinterRepository
    ): QueryPrinterStatusUseCase {
        return QueryPrinterStatusUseCase(printerRepository)
    }

    // Print Job Use Cases
    @Provides
    @Singleton
    fun provideCreatePrintJobUseCase(
        printJobRepository: PrintJobRepository,
        usbPrintRepository: UsbPrintRepository
    ): CreatePrintJobUseCase {
        return CreatePrintJobUseCase(printJobRepository, usbPrintRepository)
    }

    @Provides
    @Singleton
    fun provideGetPrintJobsUseCase(
        printJobRepository: PrintJobRepository
    ): GetPrintJobsUseCase {
        return GetPrintJobsUseCase(printJobRepository)
    }

    @Provides
    @Singleton
    fun provideCancelPrintJobUseCase(
        printJobRepository: PrintJobRepository,
        usbPrintRepository: UsbPrintRepository
    ): CancelPrintJobUseCase {
        return CancelPrintJobUseCase(printJobRepository, usbPrintRepository)
    }

    @Provides
    @Singleton
    fun provideRetryPrintJobUseCase(
        printJobRepository: PrintJobRepository
    ): RetryPrintJobUseCase {
        return RetryPrintJobUseCase(printJobRepository)
    }

    // Document Use Cases
    @Provides
    @Singleton
    fun provideGetRecentDocumentsUseCase(
        documentRepository: DocumentRepository
    ): GetRecentDocumentsUseCase {
        return GetRecentDocumentsUseCase(documentRepository)
    }

    @Provides
    @Singleton
    fun provideOpenDocumentUseCase(
        documentRepository: DocumentRepository
    ): OpenDocumentUseCase {
        return OpenDocumentUseCase(documentRepository)
    }
}
