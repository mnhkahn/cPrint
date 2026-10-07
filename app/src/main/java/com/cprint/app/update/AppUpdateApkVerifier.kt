package com.cprint.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.cprint.app.BuildConfig
import java.io.File

internal object AppUpdateApkVerifier {
    @Suppress("DEPRECATION")
    fun verify(context: Context, apk: File, expectedSize: Long, expectedVersion: Long) {
        check(apk.isFile && apk.length() == expectedSize) { "安装包不完整" }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val downloaded = checkNotNull(pm.getPackageArchiveInfo(apk.absolutePath, flags)) { "安装包无法读取" }
        val installed = pm.getPackageInfo(context.packageName, flags)
        val code = if (Build.VERSION.SDK_INT >= 28) downloaded.longVersionCode else downloaded.versionCode.toLong()
        check(downloaded.packageName == context.packageName && code == expectedVersion && code > BuildConfig.VERSION_CODE) { "安装包版本不匹配" }
        val incoming = if (Build.VERSION.SDK_INT >= 28) downloaded.signingInfo?.apkContentsSigners else downloaded.signatures
        val existing = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        check(!incoming.isNullOrEmpty() && !existing.isNullOrEmpty() && incoming.toSet() == existing.toSet()) { "安装包签名不匹配" }
    }

}
