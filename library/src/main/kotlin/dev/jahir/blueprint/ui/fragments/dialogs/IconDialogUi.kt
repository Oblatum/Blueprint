package dev.jahir.blueprint.ui.fragments.dialogs

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import dev.jahir.blueprint.R

internal class IconDialogUi(
    private val dialog: AlertDialog,
    private val animationsEnabled: Boolean,
    private val iconAnimationDelay: Long,
    private val iconAnimationDuration: Long
) {

    private var packIconShown: Boolean = false
    private var pendingAppIcon: Drawable? = null
    private var appIconAnimated: Boolean = false

    fun reset() {
        packIconShown = false
        pendingAppIcon = null
        appIconAnimated = false
        setStoreButtonVisible(false)
        hideAppIcon()
    }

    fun setStoreButtonVisible(visible: Boolean) {
        try {
            val btn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE) ?: return
            btn.visibility = if (visible) View.VISIBLE else View.GONE
            btn.isEnabled = visible
        } catch (_: Exception) {
        }
    }

    fun showPackIcon(bitmap: Bitmap?) {
        bitmap ?: return
        val iconView: AppCompatImageView = dialog.findViewById(R.id.icon) ?: return

        iconView.scaleX = 0F
        iconView.scaleY = 0F
        iconView.alpha = 0F
        iconView.translationX = 0F
        iconView.setImageBitmap(bitmap)

        if (!animationsEnabled) {
            iconView.scaleX = 1F
            iconView.scaleY = 1F
            iconView.alpha = 1F
            packIconShown = true
            tryAnimateAppIcon()
            return
        }

        iconView.animate()
            .scaleX(1F)
            .scaleY(1F)
            .alpha(1F)
            .setStartDelay(iconAnimationDelay)
            .setDuration(iconAnimationDuration)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    packIconShown = true
                    tryAnimateAppIcon()
                }
            })
            .start()
    }

    fun setAppIcon(drawable: Drawable?) {
        pendingAppIcon = drawable
        if (drawable == null) {
            hideAppIcon()
            return
        }
        tryAnimateAppIcon()
    }

    private fun hideAppIcon() {
        val appIconView: AppCompatImageView? = dialog.findViewById(R.id.app_icon)
        appIconView?.visibility = View.GONE
    }

    private fun tryAnimateAppIcon() {
        if (appIconAnimated) return
        if (!packIconShown) return
        val drawable = pendingAppIcon ?: return

        val iconView: AppCompatImageView = dialog.findViewById(R.id.icon) ?: return
        val appIconView: AppCompatImageView = dialog.findViewById(R.id.app_icon) ?: return

        appIconAnimated = true
        animateShiftThenShowAppIcon(iconView, appIconView, drawable)
    }

    // 需求：图标包图标先在原始居中位置出现；等应用图标获取完成后，
    // 再把图标包图标左移到双图标布局的左侧位置，然后应用图标再出现。
    private fun animateShiftThenShowAppIcon(
        iconView: AppCompatImageView,
        appIconView: AppCompatImageView,
        drawable: Drawable
    ) {
        appIconView.setImageDrawable(drawable)
        appIconView.scaleX = 0F
        appIconView.scaleY = 0F
        appIconView.alpha = 0F

        if (!animationsEnabled) {
            appIconView.visibility = View.VISIBLE
            appIconView.scaleX = 1F
            appIconView.scaleY = 1F
            appIconView.alpha = 1F
            return
        }

        // 先让第二个 view 占位（INVISIBLE），这样 pack icon 的最终位置会变成“左侧”。
        // 然后通过 translationX 把它“拉回居中”，再动画回到 0 达到“左移”效果。
        appIconView.visibility = View.INVISIBLE

        val params = appIconView.layoutParams as? ViewGroup.MarginLayoutParams
        val marginStartPx = params?.marginStart ?: params?.leftMargin ?: 0

        val vto: ViewTreeObserver = iconView.viewTreeObserver
        vto.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!iconView.viewTreeObserver.isAlive) return true
                iconView.viewTreeObserver.removeOnPreDrawListener(this)

                val shiftPx = (iconView.width + marginStartPx) / 2f
                iconView.translationX = shiftPx
                iconView.animate()
                    .translationX(0f)
                    .setDuration(iconAnimationDuration)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            appIconView.visibility = View.VISIBLE
                            appIconView.animate()
                                .scaleX(1F)
                                .scaleY(1F)
                                .alpha(1F)
                                .setStartDelay(0)
                                .setDuration(iconAnimationDuration)
                                .setListener(null)
                                .start()
                        }
                    })
                    .start()
                return true
            }
        })
    }
}
