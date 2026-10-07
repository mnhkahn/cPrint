package com.cprint.app.update

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.content.pm.SigningInfo
import android.content.pm.Signature
import com.cprint.app.BuildConfig
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [27, 34])
class AppUpdateApkVerifierTest {
    @Test fun `accepts matching update and rejects wrong size package version or signature`() {
        val file = File.createTempFile("update-", ".apk")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            val pm = mock<PackageManager>()
            val context = mock<Context>()
            whenever(context.packageManager).thenReturn(pm)
            whenever(context.packageName).thenReturn(BuildConfig.APPLICATION_ID)
            val incoming = PackageInfo().apply {
                packageName = BuildConfig.APPLICATION_ID
                versionCode = BuildConfig.VERSION_CODE + 1
                signatures = arrayOf(Signature("abcd"))
            }
            val installed = PackageInfo().apply { signatures = arrayOf(Signature("abcd")) }
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            if (Build.VERSION.SDK_INT >= 28) {
                incoming.signingInfo = mock<SigningInfo>().also { info ->
                    whenever(info.apkContentsSigners).thenAnswer { incoming.signatures }
                }
                installed.signingInfo = mock<SigningInfo>().also { info ->
                    whenever(info.apkContentsSigners).thenAnswer { installed.signatures }
                }
            }
            whenever(pm.getPackageArchiveInfo(file.absolutePath, flags)).thenReturn(incoming)
            whenever(pm.getPackageInfo(BuildConfig.APPLICATION_ID, flags)).thenReturn(installed)
            val version = incoming.versionCode.toLong()
            fun verify(size: Long = 3) = AppUpdateApkVerifier.verify(context, file, size, version)
            verify()
            assertThrows(IllegalStateException::class.java) { verify(4) }
            incoming.packageName = "another.app"
            assertThrows(IllegalStateException::class.java) { verify() }
            incoming.packageName = BuildConfig.APPLICATION_ID
            incoming.versionCode--
            assertThrows(IllegalStateException::class.java) { verify() }
            incoming.versionCode++
            incoming.signatures = arrayOf(Signature("1234"))
            assertThrows(IllegalStateException::class.java) { verify() }
        } finally { file.delete() }
    }
}
