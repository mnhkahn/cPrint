# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

cPrint is an Android printing application that connects to USB printers via USB Type-C. It supports printing PDFs and images with customizable settings (paper size, orientation, copies, etc.).

- **Package**: `com.cprint.app`
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material3

## Build Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Build release APK
./gradlew :app:assembleRelease

# Install debug APK to connected device
./gradlew :app:installDebug

# Compile only (check for errors)
./gradlew :app:compileDebugKotlin

# Clean build
./gradlew clean

# Run unit tests
./gradlew :app:testDebugUnitTest

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.cprint.app.ExampleUnitTest"

# Run connected instrumented tests (requires device/emulator)
./gradlew :app:connectedDebugAndroidTest

# Generate lint report
./gradlew :app:lintDebug
```

### Output Locations
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

## Architecture

The project follows **Clean Architecture** with three main layers:

### 1. Data Layer (`data/`)
- **Local**: Room database (AppDatabase), DAOs, Entity classes
- **Repository**: Implementation of domain repository interfaces
- **Model**: Entity classes for database (PrinterEntity, PrintJobEntity, etc.)

### 2. Domain Layer (`domain/`)
- **Model**: Domain models (Printer, PrintJob, PrintSettings)
- **Repository**: Interface definitions for data operations
- **Use Case**: Single-responsibility business logic classes

### 3. Presentation Layer (`presentation/`)
- **Activities**: MainActivity, PrintPreviewActivity, PrintSettingsActivity, PrintQueueActivity
- **ViewModels**: One per screen (MainViewModel, PrintPreviewViewModel, etc.)
- **Components**: Reusable Compose UI components
- **Theme**: Material3 theme configuration

### Dependency Injection
Uses **Hilt** (Dagger) for DI. Key modules:
- `di/AppModule.kt`: Provides database, DAOs, and repository implementations
- `di/UseCaseModule.kt`: Provides use case dependencies

**Note**: Manifest-registered BroadcastReceivers (like UsbDeviceReceiver) cannot use Hilt injection reliably. Use manual dependency instantiation instead.

## Key Components

### USB Printing Flow
1. **UsbDeviceReceiver**: Manifest-registered BroadcastReceiver that handles USB attach/detach events and permission callbacks
2. **UsbConnectionService**: Background service maintaining USB connection state
3. **PrintJobService**: Foreground service for executing print jobs with notifications
4. **UsbPrintRepository**: Low-level USB communication (bulk transfer, endpoints)

### Database (Room)
- **AppDatabase**: Singleton database instance with `getInstance(context)`
- **Entities**: PrinterEntity, PrintJobEntity, RecentDocumentEntity
- **DAOs**: PrinterDao, PrintJobDao, RecentDocumentDao

### Print Settings
Settings are persisted via Jetpack DataStore (`PrintSettingsRepository`).

## Important Implementation Details

### USB Permission Handling

The USB permission flow requires careful handling:

1. **Manifest-registered receiver** (`UsbDeviceReceiver`):
   - Receives `USB_DEVICE_ATTACHED` system broadcasts
   - Requests permission using `PendingIntent.getBroadcast()` with `ACTION_USB_PERMISSION`
   - Receives permission callback and handles actual printer connection
   - Cannot use Hilt injection - instantiate dependencies manually

2. **Activity-registered receiver** (in `MainActivity`):
   - **CRITICAL**: Must register in `onCreate()`, NOT `onResume()`
   - **CRITICAL**: Must unregister in `onDestroy()`, NOT `onPause()`
   - Reason: The permission dialog pauses the Activity, causing `onPause()` to fire
   - If registered in `onResume()`, the receiver gets unregistered when the dialog shows
   - This receiver updates UI state when connection succeeds/fails

### Receiver Registration Pattern (Correct)
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Register BEFORE any UI setup
    val filter = IntentFilter().apply {
        addAction(UsbDeviceReceiver.ACTION_USB_PERMISSION)
        addAction(UsbDeviceReceiver.ACTION_PRINTER_CONNECTED)
        addAction(UsbDeviceReceiver.ACTION_PRINTER_CONNECTION_FAILED)
    }
    ContextCompat.registerReceiver(this, usbPermissionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    // ... rest of setup
}

override fun onDestroy() {
    super.onDestroy()
    unregisterReceiver(usbPermissionReceiver)
}
```

### Repository Pattern
All repositories follow this pattern:
- Repository interface in `domain/repository/`
- Implementation in `data/repository/`
- Use Flow for reactive data streams
- Suspend functions for one-shot operations

### Error Handling
- Use `Result<T>` for operation results
- Timber for logging (use `Timber.d()`, `Timber.e()`)
- Toast messages for user-facing errors in UI layer

## Testing

```bash
# Unit tests (local JVM)
./gradlew :app:testDebugUnitTest

# Robolectric tests (Android tests running on JVM)
./gradlew :app:testDebugUnitTest --tests "*Robolectric*"

# Instrumented tests (requires device)
./gradlew :app:connectedDebugAndroidTest
```

### Test Structure
- Unit tests: `app/src/test/java/`
- Instrumented tests: `app/src/androidTest/java/`
- Uses JUnit 4, Mockito, MockK, Coroutines Test, Robolectric

## Build Configuration

### Repositories (settings.gradle.kts)
Uses Chinese mirror repositories for faster builds:
- Aliyun (Google, Gradle Plugin, Public)
- Huawei Cloud Maven
- JitPack for external libraries

### Key Dependencies
- **Compose BOM**: 2024.02.00 (Material3 1.2.0)
- **Kotlin**: 1.9.22
- **Hilt**: 2.50
- **Room**: 2.6.1
- **Navigation**: 2.7.7

## Debugging Tips

### View logs
```bash
# Filter for cPrint app logs
adb logcat -s UsbDeviceReceiver:D MainActivity:D Timber:D *:S

# All app logs
adb logcat | grep com.cprint.app
```

### USB Debugging
Check connected USB devices:
```bash
adb shell dumpsys usb
adb shell lsusb
```

## File Organization

```
app/src/main/java/com/cprint/app/
├── CPrintApplication.kt          # Application class (Hilt entry point)
├── di/                           # Hilt modules
├── data/
│   ├── local/                    # Room database, DAOs
│   ├── model/entity/             # Database entities
│   └── repository/               # Repository implementations
├── domain/
│   ├── model/                    # Domain models (Printer, PrintJob)
│   ├── repository/               # Repository interfaces
│   └── usecase/                  # Use cases (GetPrintersUseCase, etc.)
├── presentation/
│   ├── main/                     # MainActivity, MainViewModel, MainScreen
│   ├── preview/                  # Print preview screen
│   ├── settings/                 # Print settings screen
│   ├── queue/                    # Print queue screen
│   ├── components/               # Reusable Compose components
│   └── theme/                    # Material3 theme
├── service/
│   ├── UsbDeviceReceiver.kt      # USB broadcast receiver
│   ├── UsbConnectionService.kt   # USB connection service
│   └── PrintJobService.kt        # Print job foreground service
└── util/                         # Utility classes (PdfUtils, UsbUtils)
```

## Common Issues

1. **Compose version warnings**: Ensure Compose BOM is 2024.02.00 or later for Material3 1.2.0 components
2. **Room compilation errors**: Use Room 2.6.1+ with Kotlin 1.9.22
3. **USB permission not received**: Ensure `UsbDeviceReceiver` is properly declared in AndroidManifest.xml with exported="true"
4. **Hilt injection in BroadcastReceiver**: Manifest-registered receivers don't support Hilt injection; instantiate dependencies manually
