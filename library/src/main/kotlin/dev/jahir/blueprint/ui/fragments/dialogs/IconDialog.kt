package dev.jahir.blueprint.ui.fragments.dialogs

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.palette.graphics.Palette
import dev.jahir.blueprint.R
import dev.jahir.blueprint.data.models.Icon
import dev.jahir.blueprint.extensions.asAdaptive
import dev.jahir.blueprint.extensions.getIconDisplayName
import dev.jahir.blueprint.extensions.openAppInStore
import dev.jahir.blueprint.extensions.resolveIconPackages
import dev.jahir.blueprint.extensions.tryLoadApplicationIcon
import dev.jahir.blueprint.ui.viewholders.IconViewHolder.Companion.ICON_ANIMATION_DELAY
import dev.jahir.blueprint.ui.viewholders.IconViewHolder.Companion.ICON_ANIMATION_DURATION
import dev.jahir.frames.extensions.context.drawable
import dev.jahir.frames.extensions.context.getAppName
import dev.jahir.frames.extensions.fragments.mdDialog
import dev.jahir.frames.extensions.fragments.negativeButton
import dev.jahir.frames.extensions.fragments.positiveButton
import dev.jahir.frames.extensions.fragments.preferences
import dev.jahir.frames.extensions.fragments.title
import dev.jahir.frames.extensions.fragments.view
import dev.jahir.frames.extensions.resources.asBitmap
import dev.jahir.frames.extensions.resources.luminance
import dev.jahir.frames.extensions.utils.bestSwatch
import kotlin.concurrent.thread

class IconDialog : DialogFragment() {

    private var icon: Icon? = null
    private var dialog: AlertDialog? = null
    private var storePackageToOpen: String? = null
    private var ui: IconDialogUi? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        val titleText = icon?.let { requireContext().getIconDisplayName(it) }
            ?: requireContext().getAppName()
        dialog = requireContext().mdDialog {
            title(titleText)
            view(R.layout.item_dialog_icon)
            negativeButton(R.string.get_from_store) {
                storePackageToOpen?.let { pkg -> context?.openAppInStore(pkg) }
            }
            positiveButton(dev.jahir.frames.R.string.close) { dismiss() }
        }
        dialog?.setOnShowListener { onDialogShown() }
        return dialog!!
    }

    private fun onDialogShown() {
        val dialogRef = dialog ?: return
        ui = IconDialogUi(
            dialogRef,
            preferences.animationsEnabled,
            ICON_ANIMATION_DELAY,
            ICON_ANIMATION_DURATION
        ).also { it.reset() }

        icon?.let { icon ->
            val bitmap = try {
                context?.drawable(icon.resId)?.asAdaptive(context)?.first?.asBitmap()
            } catch (e: Exception) {
                null
            }
            bitmap?.let {
                ui?.showPackIcon(bitmap)
                resolveAndBindAppInfoAsync(icon)
                try {
                    Palette.from(it)
                        .generate { palette ->
                            setButtonColor(palette?.bestSwatch)
                        }
                } catch (e: Exception) {
                }
            } ?: { dismiss() }()
        }
    }

    private fun resolveAndBindAppInfoAsync(icon: Icon) {
        val ctx = context ?: return
        thread(start = true, name = "IconDialogResolve") {
            val resolution = try {
                ctx.resolveIconPackages(icon)
            } catch (_: Exception) {
                null
            }
            val appDrawable: Drawable? = try {
                resolution?.installedPackage?.let { ctx.tryLoadApplicationIcon(it) }
            } catch (_: Exception) {
                null
            }

            val storePkg = resolution?.storePackage

            if (!isAdded) return@thread
            val dialogRef = dialog ?: return@thread
            dialogRef.window?.decorView?.post {
                if (!isAdded) return@post

                storePackageToOpen = storePkg
                ui?.setStoreButtonVisible(!storePkg.isNullOrBlank())
                ui?.setAppIcon(appDrawable)
            }
        }
    }

    private fun setButtonColor(swatch: Palette.Swatch? = null) {
        swatch ?: return
        val currentNightMode = try {
            context?.let {
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            } ?: Configuration.UI_MODE_NIGHT_UNDEFINED
        } catch (e: Exception) {
            Configuration.UI_MODE_NIGHT_UNDEFINED
        }
        val isDarkTheme = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        val bestColor = swatch.rgb
        if (!isDarkTheme && bestColor.luminance > (LUMINANCE_THRESHOLD - .1F)) return
        if (isDarkTheme && bestColor.luminance < (LUMINANCE_THRESHOLD + .1F)) return
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(bestColor)
    }

    fun show(fragmentActivity: FragmentActivity) {
        show(fragmentActivity.supportFragmentManager, TAG)
    }

    override fun show(manager: FragmentManager, tag: String?) {
        super.show(manager, TAG)
    }

    override fun show(transaction: FragmentTransaction, tag: String?): Int =
        super.show(transaction, TAG)

    companion object {
        private const val TAG = "icon_dialog_fragment"
        private const val LUMINANCE_THRESHOLD = .35F
        fun create(icon: Icon?) = IconDialog().apply { this.icon = icon }
    }
}
