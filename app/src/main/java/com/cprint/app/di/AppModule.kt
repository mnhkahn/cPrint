package com.cprint.app.di

import android.content.Context
import com.cprint.app.data.local.AppDatabase
import com.cprint.app.data.local.PrintJobDao
import com.cprint.app.data.local.PrinterDao
import com.cprint.app.data.local.RecentDocumentDao
import com.cprint.app.data.repository.DocumentRepositoryImpl
import com.cprint.app.data.repository.PrintJobRepositoryImpl
import com.cprint.app.data.repository.PrinterRepositoryImpl
import com.cprint.app.data.repository.UsbPrintRepositoryImpl
import com.cprint.app.domain.repository.DocumentRepository
import com.cprint.app.domain.repository.PrintJobRepository
import com.cprint.app.domain.repository.PrinterRepository
import com.cprint.app.domain.repository.UsbPrintRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Application-level dependency injection module
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun providePrintJobDao(database: AppDatabase): PrintJobDao {
        return database.printJobDao()
    }

    @Provides
    @Singleton
    fun providePrinterDao(database: AppDatabase): PrinterDao {
        return database.printerDao()
    }

    @Provides
    @Singleton
    fun provideRecentDocumentDao(database: AppDatabase): RecentDocumentDao {
        return database.recentDocumentDao()
    }

    @Provides
    @Singleton
    fun providePrintJobRepository(
        printJobDao: PrintJobDao
    ): PrintJobRepository {
        return PrintJobRepositoryImpl(printJobDao)
    }

    @Provides
    @Singleton
    fun providePrinterRepository(
        @ApplicationContext context: Context,
        printerDao: PrinterDao
    ): PrinterRepository {
        return PrinterRepositoryImpl(context, printerDao)
    }

    @Provides
    @Singleton
    fun provideDocumentRepository(
        @ApplicationContext context: Context,
        recentDocumentDao: RecentDocumentDao
    ): DocumentRepository {
        return DocumentRepositoryImpl(context, recentDocumentDao)
    }

    @Provides
    @Singleton
    fun provideUsbPrintRepository(
        @ApplicationContext context: Context
    ): UsbPrintRepository {
        return UsbPrintRepositoryImpl(context)
    }
}
