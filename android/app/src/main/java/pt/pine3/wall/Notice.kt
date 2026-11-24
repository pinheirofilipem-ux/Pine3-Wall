package pt.pine3.wall

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Mensagem enviada pelo serviço pine3_wall.notify (ex.: "O jantar está na mesa"): fecha ao tocar ou ao fim de [seconds]. */
class Notice(private val act: Activity, private val kit: Kit, private val title: String, private val message: String, private val seconds: Int) {
    private val dialog = PokeDialog(act).apply { closeOnIdle = false }
    private val h = Handler(Looper.getMainLooper())

    fun show() {
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = kit.rounded(Color.argb(235, 30, 22, 34), 32); setPadding(kit.dp(36), kit.dp(30), kit.dp(36), kit.dp(26))
        }
        card.addView(FrameLayout(act).apply {
            background = kit.circle(kit.accent)
            addView(kit.icon("mdi:message-text-outline", kit.ink, 30), FrameLayout.LayoutParams(kit.dp(30), kit.dp(30), Gravity.CENTER))
        }, LinearLayout.LayoutParams(kit.dp(60), kit.dp(60)))
        if (title.isNotBlank()) card.addView(kit.text(title, 30f, Color.WHITE, 400, display = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(16) })
        card.addView(kit.text(message, if (title.isBlank()) 28f else 20f, Color.argb(225, 255, 255, 255), if (title.isBlank()) 400 else 400, display = title.isBlank()).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(if (title.isBlank()) 16 else 10) })
        card.addView(kit.pill(Tr.s("OK", "OK"), "mdi:check", true) { dismiss() }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(22) })
        val root = FrameLayout(act).apply {
            setBackgroundColor(Color.argb(150, 10, 4, 12)); isClickable = true; setOnClickListener { dismiss() }
            addView(card, FrameLayout.LayoutParams(kit.dp(kit.clamp(320f, kit.wdp * 0.6f, 720f)), -2, Gravity.CENTER))
        }
        dialog.setContentView(root)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        dialog.setOnDismissListener { h.removeCallbacksAndMessages(null) }
        immersive(dialog)
        dialog.show()
        Elastic.pop(card, 0, 0.9f)
        h.postDelayed({ dismiss() }, seconds.coerceIn(3, 3600) * 1000L)
    }

    fun dismiss() { if (dialog.isShowing) dialog.dismiss() }
}
