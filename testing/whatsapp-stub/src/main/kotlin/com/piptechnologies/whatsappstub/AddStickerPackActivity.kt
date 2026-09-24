package com.piptechnologies.whatsappstub

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Handles com.whatsapp.intent.action.ENABLE_STICKER_PACK like WhatsApp's own
 * confirm dialog: reads the pack back through the sticker app's provider,
 * shows what it found with the contract check, and answers RESULT_OK (after
 * recording the pack for the whitelist provider) or RESULT_CANCELED with a
 * validation_error extra when the pack breaks the rules.
 */
class AddStickerPackActivity : Activity() {

    private var authority = ""
    private var identifier = ""
    private var report: ContractReport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        identifier = intent.getStringExtra(EXTRA_STICKER_PACK_ID).orEmpty()
        authority = intent.getStringExtra(EXTRA_STICKER_PACK_AUTHORITY).orEmpty()
        val requestedName = intent.getStringExtra(EXTRA_STICKER_PACK_NAME).orEmpty()
        val checked = ContractCheck(contentResolver, authority, identifier).run()
        report = checked
        setContentView(content(checked, requestedName))
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun content(report: ContractReport, requestedName: String): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply {
            text = "WhatsApp stub · sticker contract check"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(MUTED)
        })

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(10))
        }
        header.addView(ImageView(this).apply {
            setImageBitmap(report.tray)
            contentDescription = "stub-tray"
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        titles.addView(TextView(this).apply {
            text = "Add “${report.name.ifBlank { requestedName }}” to WhatsApp?"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(INK)
        })
        titles.addView(TextView(this).apply {
            val kind = if (report.animated) " · animated" else ""
            text = "${report.stickerCount} stickers · ${report.publisher}$kind"
            textSize = 13f
            setTextColor(MUTED)
        })
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)

        val previews = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        report.previews.forEach { bitmap ->
            previews.addView(ImageView(this).apply { setImageBitmap(bitmap) },
                LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginEnd = dp(6) })
        }
        root.addView(previews)

        root.addView(TextView(this).apply {
            text = (report.passed.map { "✓ $it" } + report.problems.map { "✗ $it" }).joinToString("\n")
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(if (report.ok) PASS else FAIL)
            setPadding(0, dp(12), 0, dp(8))
            contentDescription = if (report.ok) "stub-contract-ok" else "stub-contract-failed"
        })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        buttons.addView(Button(this).apply {
            text = "Cancel"
            isAllCaps = false
            contentDescription = "stub-cancel"
            setOnClickListener { cancel() }
        })
        buttons.addView(Button(this).apply {
            text = "Add pack"
            isAllCaps = false
            isEnabled = report.ok
            contentDescription = "stub-add"
            setOnClickListener { add() }
        })
        root.addView(buttons)
        return root
    }

    private fun add() {
        Whitelist.add(this, authority, identifier)
        setResult(RESULT_OK)
        finish()
    }

    private fun cancel() {
        val problem = report?.problems?.firstOrNull()
        if (problem != null) {
            setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_VALIDATION_ERROR, problem))
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
        const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
        const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"
        const val EXTRA_VALIDATION_ERROR = "validation_error"

        val INK = Color.parseColor("#1E2128")
        val MUTED = Color.parseColor("#6B7280")
        val PASS = Color.parseColor("#15803D")
        val FAIL = Color.parseColor("#B91C1C")
    }
}
