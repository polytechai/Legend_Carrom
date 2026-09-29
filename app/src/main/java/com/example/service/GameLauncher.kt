package com.example.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

object GameLauncher {

    const val TARGET_PACKAGE = "com.miniclip.carrom"

    /**
     * Checks if target game is installed on the device.
     */
    fun isGameInstalled(context: Context, packageName: String = TARGET_PACKAGE): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Launches the game package, or falls back to Google Play Store if not installed.
     * @return true if game was launched directly, false if redirected to store.
     */
    fun launchGame(context: Context, packageName: String = TARGET_PACKAGE): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(launchIntent)
            true
        } else {
            Toast.makeText(context, "Game not installed. Opening Play Store...", Toast.LENGTH_SHORT).show()
            openPlayStore(context, packageName)
            false
        }
    }

    /**
     * Redirects user to Google Play Store listing for the specified package.
     */
    fun openPlayStore(context: Context, packageName: String = TARGET_PACKAGE) {
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
