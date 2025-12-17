/*
 * Copyright (c) 2025 Ding2FTW
 * This file is part of Blueprint and is licensed under CC-BY-SA 4.0.
 * See LICENSE.md for details.
 */
package dev.jahir.blueprint.extensions

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import dev.jahir.frames.extensions.resources.hasContent

/**
 * Retrieves the original app icon, bypassing ROM theme system overlays.
 *
 * Theme engines (MIUI, ColorOS, OneUI, etc.) hook into PackageManager APIs
 * to replace app icons. This function bypasses those hooks by:
 * 1. Creating an isolated package context with CONTEXT_IGNORE_SECURITY
 * 2. Accessing resources directly from the target app's APK
 * 3. Requesting high-density resources that themes typically don't overlay
 */
fun Context.getOriginalAppIcon(pkg: String): Drawable? {
    if (!pkg.hasContent()) return null

    return runCatching {
        val packageContext = createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY)
        val appInfo = packageManager.getApplicationInfoCompat(pkg, 0)

        // Resource IDs to try: icon first, then logo as fallback
        val resourceIds = listOfNotNull(
            appInfo.icon.takeIf { it != 0 },
            appInfo.logo.takeIf { it != 0 }
        )

        // Densities from highest to lowest - theme engines typically only overlay default density
        val densities = intArrayOf(
            DisplayMetrics.DENSITY_XXXHIGH,  // 640 dpi
            DisplayMetrics.DENSITY_XXHIGH,   // 480 dpi
            DisplayMetrics.DENSITY_XHIGH,    // 320 dpi
            DisplayMetrics.DENSITY_HIGH,     // 240 dpi
            DisplayMetrics.DENSITY_MEDIUM,   // 160 dpi
            DisplayMetrics.DENSITY_TV        // 213 dpi (fallback)
        )

        // Try each resource ID with each density
        for (resId in resourceIds) {
            for (density in densities) {
                runCatching {
                    packageContext.resources.getDrawableForDensity(resId, density, null)
                }.getOrNull()?.let { return@runCatching it }
            }
        }

        null
    }.getOrNull()
}
