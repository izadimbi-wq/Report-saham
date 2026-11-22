package com.izadimbi.reportsaham

import android.app.Activity
import android.content.ContentValues
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var db: PortfolioDb
    private lateinit var shareholder: Spinner
    private lateinit var symbol: EditText
    private lateinit var qty: EditText
    private lateinit var price: EditText
    private lateinit var type: Spinner
    private lateinit var date: EditText
    private lateinit var status: TextView
    private lateinit var list: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = PortfolioDb(this)
        buildUi()
        refresh()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(TextView(this).apply {
            text = "گزارش سهام — ثبت معاملات ۶ سهامدار"
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 18)
        })

        shareholder = Spinner(this)
        shareholder.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            db.shareholders()
        )
        addField(root, "سهامدار", shareholder)

        symbol = edit("فولاد")
        addField(root, "نماد", symbol)

        qty = edit("0")
        qty.inputType = InputType.TYPE_CLASS_NUMBER
        addField(root, "تعداد سهم", qty)

        price = edit("0")
        price.inputType = InputType.TYPE_CLASS_NUMBER
        addField(root, "قیمت", price)

        type = Spinner(this)
        type.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("خرید", "فروش")
        )
        addField(root, "نوع معامله", type)

        date = edit(today())
        addField(root, "تاریخ (YYYY-MM-DD)", date)

        root.addView(Button(this).apply {
            text = "ثبت معامله"
            setOnClickListener { saveTransaction() }
        })

        val reportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val csv = Button(this).apply { text = "گزارش Excel/CSV" }
        val pdf = Button(this).apply { text = "گزارش PDF" }

        reportRow.addView(csv, LinearLayout.LayoutParams(0, WRAP, 1f))
        reportRow.addView(pdf, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(reportRow)

        csv.setOnClickListener { exportCsv() }
        pdf.setOnClickListener { exportPdf() }

        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, 12, 0, 8)
        }
        root.addView(status)

        list = TextView(this).apply { textSize = 13f }
        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        setContentView(root)
    }

    private fun addField(root: LinearLayout, label: String, view: View) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 13f
        })
        root.addView(view, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun edit(value: String) = EditText(this).apply {
        setText(value)
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun today() =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun saveTransaction() {
        val q = qty.text.toString().toLongOrNull()
        val p = price.text.toString().toLongOrNull()

        if (q == null || q <= 0 || p == null || p < 0 || symbol.text.isBlank()) {
            status.text = "اطلاعات معامله صحیح نیست."
            return
        }

        db.insert(
            shareholder.selectedItem.toString(),
            symbol.text.toString().trim().uppercase(Locale.US),
            type.selectedItem.toString(),
            q,
            p,
            date.text.toString().trim()
        )

        status.text = "معامله با موفقیت ثبت شد."
        refresh()
    }

    private fun refresh() {
        if (!::list.isInitialized) return
        list.text = db.recent(100).joinToString("\n")
    }

    private fun exportCsv() {
        val csv = buildString {
            append("سهامدار,نماد,نوع,تعداد,قیمت,تاریخ\n")
            db.all().forEach { row ->
                append(
                    row.joinToString(",") { value ->
                        """ + value.replace(""", """") + """
                    }
                ).append("\n")
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "Report-saham-${today()}.csv")
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download")
        }

        contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        )?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(csv.toByteArray(Charsets.UTF_8))
            }
            status.text = "گزارش Excel/CSV در پوشه Download ذخیره شد."
        }
    }

    private fun exportPdf() {
        val document = PdfDocument()
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(595, 842, 1).create()
        )

        val canvas = page.canvas
        val paint = Paint().apply {
            textSize = 11f
            color = Color.BLACK
        }

        var y = 40f
        canvas.drawText("Report Saham - Portfolio Transactions", 30f, y, paint)
        y += 25f

        db.all().take(35).forEach { row ->
            val line = row.joinToString(" | ")
            canvas.drawText(line.take(100), 30f, y, paint)
            y += 18f
        }

        document.finishPage(page)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "Report-saham-${today()}.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download")
        }

        contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        )?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { out ->
                document.writeTo(out)
            }
            status.text = "گزارش PDF در پوشه Download ذخیره شد."
        }

        document.close()
    }

    companion object {
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
