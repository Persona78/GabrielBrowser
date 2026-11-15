package pt.gabriel.browser

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

enum class BtnStyle { PRIMARY, SECONDARY, DANGER }

data class Tile(
    val icon: String,
    val label: String,
    val active: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Painel inferior (bottom sheet) com visual próprio: cantos arredondados, cartões,
 * mosaicos e botões. Usa as cores do tema (claro/escuro) definidas em res/values.
 */
class Sheet(private val a: Activity, title: String, subtitle: String? = null) {

    private val density = a.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private fun col(id: Int) = a.getColor(id)

    val dialog = Dialog(a)
    private val body = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private val footer = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(20))
            background = GradientDrawable().apply {
                setColor(col(R.color.surface))
                val r = dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }

        // pega
        root.addView(View(a).apply {
            background = GradientDrawable().apply {
                setColor(col(R.color.divider)); cornerRadius = dp(3).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(40), dp(5)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(14)
        })

        root.addView(TextView(a).apply {
            text = I18n.tr(title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(col(R.color.text))
        })
        if (subtitle != null) {
            root.addView(TextView(a).apply {
                text = I18n.tr(subtitle)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(col(R.color.text_secondary))
                setPadding(0, dp(2), 0, 0)
            })
        }

        val scroll = ScrollView(a).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, dp(14), 0, dp(6))
            clipToPadding = false
            addView(body)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(footer)

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setWindowAnimations(android.R.style.Animation_InputMethod)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // altura máxima: 85% do ecrã
            val h = (a.resources.displayMetrics.heightPixels * 0.85f).toInt()
            root.minimumHeight = 0
            attributes = attributes.also { it.dimAmount = 0.45f }
            root.post {
                if (root.height > h) {
                    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h)
                }
            }
        }
    }

    // ------------------------------------------------------------ conteúdo

    fun text(msg: String): Sheet {
        body.addView(TextView(a).apply {
            text = I18n.tr(msg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            setTextColor(col(R.color.text))
            setLineSpacing(0f, 1.15f)
            setPadding(0, 0, 0, dp(10))
        })
        return this
    }

    fun note(msg: String): Sheet {
        body.addView(TextView(a).apply {
            text = I18n.tr(msg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(col(R.color.text_secondary))
            setPadding(0, dp(4), 0, dp(8))
        })
        return this
    }

    fun section(label: String): Sheet {
        body.addView(TextView(a).apply {
            text = I18n.tr(label).uppercase()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.08f
            setTextColor(col(R.color.accent))
            setPadding(dp(2), dp(10), 0, dp(6))
        })
        return this
    }

    /** Linha de informação: ícone + título + detalhe. */
    fun info(icon: String, title: String, detail: String): Sheet {
        body.addView(cardRow(icon, title, detail, null, emptyList(), null))
        return this
    }

    /** Mosaicos em grelha. */
    fun tiles(items: List<Tile>, cols: Int = 3): Sheet {
        items.chunked(cols).forEach { rowItems ->
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { t ->
                row.addView(
                    tileView(t),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
                )
            }
            repeat(cols - rowItems.size) {
                row.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f)
                    .apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            body.addView(row)
        }
        return this
    }

    /** Cartão com ícone/letra, título, detalhe, botões de ação e/ou interruptor. */
    fun card(
        icon: String,
        title: String,
        detail: String? = null,
        onClick: (() -> Unit)? = null,
        actions: List<Pair<String, () -> Unit>> = emptyList(),
        toggle: Pair<Boolean, (Boolean) -> Unit>? = null
    ): Sheet {
        body.addView(cardRow(icon, title, detail, onClick, actions, toggle))
        return this
    }

    /** Caixa de texto; devolve o EditText para ler o valor. */
    fun input(hint: String, value: String = ""): EditText {
        val e = EditText(a).apply {
            setText(value)
            this.hint = I18n.tr(hint)
            setSingleLine(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(col(R.color.text))
            setHintTextColor(col(R.color.text_secondary))
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply {
                setColor(col(R.color.pill)); cornerRadius = dp(16).toFloat()
            }
        }
        body.addView(e, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
        return e
    }

    fun check(label: String, checked: Boolean): CheckBox {
        val c = CheckBox(a).apply {
            text = I18n.tr(label)
            isChecked = checked
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(col(R.color.text))
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = GradientDrawable().apply {
                setColor(col(R.color.pill)); cornerRadius = dp(16).toFloat()
            }
        }
        body.addView(c, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
        return c
    }

    /** Botão no corpo (por exemplo, opções largas). */
    fun wideButton(label: String, style: BtnStyle = BtnStyle.SECONDARY, onClick: () -> Unit): Sheet {
        body.addView(makeButton(label, style, onClick), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { bottomMargin = dp(8) })
        return this
    }

    // ------------------------------------------------------------ rodapé

    /** Botões do rodapé: até 2 lado a lado; mais do que isso, empilhados. */
    fun actions(vararg b: Triple<String, BtnStyle, () -> Unit>): Sheet {
        footer.removeAllViews()
        footer.setPadding(0, dp(8), 0, 0)
        if (b.size <= 2) {
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            b.forEachIndexed { i, t ->
                row.addView(makeButton(t.first, t.second, t.third), LinearLayout.LayoutParams(0, dp(50), 1f)
                    .apply { if (i > 0) marginStart = dp(10) })
            }
            footer.addView(row)
        } else {
            b.forEachIndexed { i, t ->
                footer.addView(makeButton(t.first, t.second, t.third), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
                ).apply { if (i > 0) topMargin = dp(8) })
            }
        }
        return this
    }

    fun show(): Sheet {
        dialog.show()
        return this
    }

    fun dismiss() = dialog.dismiss()

    // ------------------------------------------------------------ peças internas

    private fun makeButton(label: String, style: BtnStyle, onClick: () -> Unit): Button =
        Button(a).apply {
            text = I18n.tr(label)
            isAllCaps = false
            stateListAnimator = null
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            val (bg, fg) = when (style) {
                BtnStyle.PRIMARY -> col(R.color.accent) to col(R.color.bg)
                BtnStyle.SECONDARY -> col(R.color.pill) to col(R.color.text)
                BtnStyle.DANGER -> Color.parseColor("#C62828") to Color.WHITE
            }
            setTextColor(fg)
            background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(26).toFloat() }
            setOnClickListener { dialog.dismiss(); onClick() }
        }

    private fun circle(icon: String, size: Int, tint: Int): TextView = TextView(a).apply {
        text = icon
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (size >= 48) 22f else 18f)
        setTextColor(col(R.color.accent))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(tint)
        }
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun accentSoft(): Int = (col(R.color.accent) and 0x00FFFFFF) or 0x26000000

    private fun tileView(t: Tile): View {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(14), dp(6), dp(12))
            background = GradientDrawable().apply {
                setColor(if (t.active) accentSoft() else col(R.color.pill))
                cornerRadius = dp(18).toFloat()
                if (t.active) setStroke(dp(2), col(R.color.accent))
            }
            isClickable = true
            setOnClickListener { dialog.dismiss(); t.onClick() }
        }
        box.addView(circle(t.icon, 48, if (t.active) Color.TRANSPARENT else col(R.color.surface)))
        box.addView(TextView(a).apply {
            text = I18n.tr(t.label)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(col(R.color.text))
            setPadding(0, dp(8), 0, 0)
        })
        return box
    }

    private fun cardRow(
        icon: String,
        title: String,
        detail: String?,
        onClick: (() -> Unit)?,
        actions: List<Pair<String, () -> Unit>>,
        toggle: Pair<Boolean, (Boolean) -> Unit>?
    ): View {
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(col(R.color.pill)); cornerRadius = dp(18).toFloat()
            }
            if (onClick != null) {
                isClickable = true
                setOnClickListener { dialog.dismiss(); onClick() }
            }
        }
        row.addView(circle(icon, 42, accentSoft()))

        val texts = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        texts.addView(TextView(a).apply {
            text = I18n.tr(title)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(col(R.color.text))
        })
        if (!detail.isNullOrEmpty()) {
            texts.addView(TextView(a).apply {
                text = I18n.tr(detail)
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(col(R.color.text_secondary))
            })
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (toggle != null) {
            row.addView(Switch(a).apply {
                isChecked = toggle.first
                setOnCheckedChangeListener { _, on -> toggle.second(on) }
            })
        }
        actions.forEach { (label, fn) ->
            row.addView(TextView(a).apply {
                text = I18n.tr(label)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(col(R.color.text))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(col(R.color.surface))
                }
                isClickable = true
                setOnClickListener { dialog.dismiss(); fn() }
            }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(6) })
        }

        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        row.layoutParams = lp
        return row
    }
}
