package com.izadimbi.reportsaham

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var db: PortfolioDb
    private lateinit var shareholder: Spinner
    private lateinit var month: EditText
    private lateinit var table: TableLayout
    private lateinit var status: TextView
    private lateinit var summaryAssets: TextView
    private lateinit var summaryPortfolio: TextView
    private lateinit var summaryPl: TextView
    private lateinit var summaryDailyPl: TextView
    private lateinit var summaryCash: TextView
    private lateinit var summaryHeading: TextView
    private lateinit var portfolioTable: TableLayout
    private lateinit var portfolioDate: TextView
    private lateinit var shareholderPhoto: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = PortfolioDb(this)
        buildUi()
        refreshTable()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 10, 10, 10)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(TextView(this).apply {
            text = "گزارش سهام — جدول ماهانه سهامدار"
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        })

        // نوار تب‌های اصلی برنامه
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 6)
        }
        val tabButtons = listOf("سهامداران", "جدول نمادها", "گزارش‌ها", "معاملات و سوابق")
        val tabContents = mutableListOf<LinearLayout>()
        val tabButtonViews = mutableListOf<Button>()

        fun selectTab(index: Int) {
            tabContents.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
            tabButtonViews.forEachIndexed { i, b ->
                b.setTypeface(null, if (i == index) Typeface.BOLD else Typeface.NORMAL)
                b.alpha = if (i == index) 1f else 0.72f
            }
        }

        tabButtons.forEachIndexed { i, title ->
            val b = Button(this).apply {
                text = title
                textSize = 10f
                setPadding(0, 2, 0, 2)
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener { selectTab(i) }
            }
            tabButtonViews.add(b)
            tabBar.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(tabBar)

        val contentFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(contentFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        repeat(4) {
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            tabContents.add(panel)
            contentFrame.addView(panel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        }

        // تب ۱: سهامداران
        val shareholderPanel = tabContents[0]
        val shareholderRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        shareholder = Spinner(this)
        shareholder.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, db.shareholders())
        shareholderRow.addView(shareholder, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        shareholderPhoto = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_myplaces)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(72, 72)
        }
        shareholderRow.addView(shareholderPhoto)
        shareholderPanel.addView(TextView(this).apply { text = "سهامدار"; textSize = 13f })
        shareholderPanel.addView(shareholderRow)

        val shareholderActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        shareholderActions.addView(Button(this).apply { text = "افزودن"; setOnClickListener { addShareholderDialog() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        shareholderActions.addView(Button(this).apply { text = "ویرایش"; setOnClickListener { editShareholderDialog() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        shareholderActions.addView(Button(this).apply { text = "حذف"; setOnClickListener { deactivateShareholderDialog() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        shareholderPanel.addView(shareholderActions)

        shareholderPanel.addView(TextView(this).apply {
            text = "مدیریت مشخصات و عکس سهامداران"
            textSize = 13f
            setPadding(0, 10, 0, 4)
        })

        // تب ۲: جدول نمادها
        val portfolioPanel = tabContents[1]
        portfolioDate = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 4, 0, 4)
        }
        portfolioPanel.addView(portfolioDate)
        val portfolioScroll = HorizontalScrollView(this)
        val portfolioVerticalScroll = ScrollView(this).apply { isFillViewport = true }
        portfolioTable = TableLayout(this).apply { isStretchAllColumns = false }
        portfolioVerticalScroll.addView(portfolioTable, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
        portfolioScroll.addView(portfolioVerticalScroll, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
        portfolioPanel.addView(portfolioScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        portfolioPanel.addView(Button(this).apply {
            text = "ورود پرتفوی Excel کارگزاری"
            setOnClickListener { pickExcel(REQUEST_PORTFOLIO) }
        })

        // تب ۳: گزارش‌ها
        val reportsPanel = tabContents[2]
        val reportControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        month = edit(toPersianDigits(currentMonth())).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "۱۴۰۵-۰۹"
        }
        reportControls.addView(month, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        reportControls.addView(Button(this).apply {
            text = "نمایش جدول"
            setOnClickListener { refreshTable() }
        })
        reportsPanel.addView(TextView(this).apply { text = "ماه گزارش (شمسی)"; textSize = 13f })
        reportsPanel.addView(reportControls)

        summaryHeading = TextView(this).apply {
            text = "خلاصه وضعیت مالی — آخرین اطلاعات ثبت‌شده"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 3)
        }
        reportsPanel.addView(summaryHeading)
        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 3, 0, 6)
        }
        fun summaryRow(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val rowOne = summaryRow()
        val rowTwo = summaryRow()
        val assetsCard = summaryCard("ارزش کل دارایی").also { summaryAssets = it.second }
        val portfolioCard = summaryCard("ارزش کل پرتفوی").also { summaryPortfolio = it.second }
        val plCard = summaryCard("سود و زیان پرتفوی").also { summaryPl = it.second }
        val dailyPlCard = summaryCard("سود و زیان روزانه").also { summaryDailyPl = it.second }
        val cashCard = summaryCard("مانده نقدی").also { summaryCash = it.second }
        val cardParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(3, 3, 3, 3)
        }
        rowOne.addView(assetsCard.first, LinearLayout.LayoutParams(cardParams))
        rowOne.addView(portfolioCard.first, LinearLayout.LayoutParams(cardParams))
        rowTwo.addView(plCard.first, LinearLayout.LayoutParams(cardParams))
        rowTwo.addView(dailyPlCard.first, LinearLayout.LayoutParams(cardParams))
        summary.addView(rowOne)
        summary.addView(rowTwo)
        summary.addView(cashCard.first, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(3, 3, 3, 3) })
        reportsPanel.addView(summary)

        val reportScroll = HorizontalScrollView(this)
        val reportVertical = ScrollView(this).apply { isFillViewport = true }
        table = TableLayout(this).apply { isStretchAllColumns = false }
        reportVertical.addView(table, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        reportScroll.addView(reportVertical, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
        reportsPanel.addView(reportScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val exportRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        exportRow.addView(Button(this).apply { text = "CSV"; setOnClickListener { exportCsv() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, .5f))
        exportRow.addView(Button(this).apply { text = "PDF"; setOnClickListener { exportPdf() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, .5f))
        reportsPanel.addView(exportRow)

        // تب ۴: معاملات و سوابق
        val historyPanel = tabContents[3]
        historyPanel.addView(Button(this).apply {
            text = "ورود سفارشات Excel کارگزاری"
            setOnClickListener { pickExcel(REQUEST_ORDERS) }
        })
        historyPanel.addView(Button(this).apply {
            text = "سوابق پرتفوی"
            setOnClickListener { showPortfolioHistory() }
        })
        historyPanel.addView(TextView(this).apply {
            text = "خرید و فروش‌های انجام‌شده و سوابق پرتفوی از این بخش مدیریت می‌شود."
            textSize = 13f
            setPadding(0, 12, 0, 0)
        })

        status = TextView(this).apply { textSize = 13f; setPadding(0, 6, 0, 0) }
        root.addView(status)
        root.addView(TextView(this).apply { text = "حقوق مؤلف: @izadimbi"; textSize = 11f; gravity = Gravity.CENTER; setPadding(0, 6, 0, 2) })

        shareholder.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshShareholderPhoto()
                refreshTable()
            }
        }

        selectTab(0)
        setContentView(root)
    }

    private fun summaryCard(title: String): Pair<LinearLayout, TextView> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(6, 6, 6, 6)
            setBackgroundResource(android.R.drawable.editbox_background)
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 11f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        })
        val value = TextView(this).apply {
            text = "—"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 5, 0, 0)
        }
        card.addView(value)
        return card to value
    }

    private fun refreshTable() {
        if (!::table.isInitialized || !::shareholder.isInitialized) return
        val ym = normalizeDigits(month.text.toString().trim()).replace('/', '-')
        if (!Regex("\\d{4}-\\d{2}").matches(ym) || ym.substring(0, 4).toInt() < 1300 || ym.substring(0, 4).toInt() > 1600) {
            status.text = "ماه را به شکل ۱۴۰۵-۰۹ وارد کنید."
            return
        }
        month.setText(toPersianDigits(ym))
        refreshSummary()
        refreshPortfolioTable()
        table.removeAllViews()
        val headers = listOf("تاریخ", "موجودی نقدی", "سود و زیان روز", "واریز", "برداشت", "خرید", "فروش")
        val header = TableRow(this)
        headers.forEach { h -> header.addView(cell(h, true)) }
        table.addView(header)
        val days = jalaliMonthDays(ym.substring(0, 4).toInt(), ym.substring(5, 7).toInt())
        for (day in 1..days) {
            val date = "$ym-${day.toString().padStart(2, '0')}"
            val who = shareholder.selectedItem.toString()
            val report = db.dailyReport(who, date)
            val totals = db.dailyTotals(who, date)
            val cash = db.cashBalance(who, date)
            val hasSnapshot = db.hasPortfolioSnapshot(who, date)
            val snapshot = db.portfolioSummary(who, date)
            val portfolioValue = snapshot[0]
            // «سود و زیان روز» فقط از ستون «سود و زیان امروز» همان Snapshot خوانده می‌شود؛
            // هرگز از «ارزش کل پرتفوی» یا اختلاف ارزش دو روز محاسبه نمی‌شود.
            val dailyPl = if (hasSnapshot) db.importedDailyPl(who, date) else report[3]
            val row = TableRow(this)
            val dayCell = cell(toPersianDigits(day.toString()), false)
            val cashCell = financialCell(cash, false)
            val dailyPlCell = financialCell(dailyPl, false)
            val depositCell = financialCell(report[4], false)
            val withdrawalCell = financialCell(report[5], false)
            val buyCell = financialCell(totals.first, false)
            val sellCell = financialCell(totals.second, false)
            val openEditor: (View) -> Unit = { openDayDialog(date) }
            listOf(dayCell, cashCell, dailyPlCell, depositCell, withdrawalCell, buyCell, sellCell).forEach {
                it.isClickable = true
                it.setOnClickListener(openEditor)
            }
            row.addView(dayCell)
            row.addView(cashCell)
            row.addView(dailyPlCell)
            row.addView(depositCell)
            row.addView(withdrawalCell)
            row.addView(buyCell)
            row.addView(sellCell)
            table.addView(row)
        }
        status.text = "${toPersianDigits(days.toString())} روز نمایش داده شد. برای ویرایش هر روز روی ردیف آن بزنید."
    }

    private fun refreshPortfolioTable() {
        if (!::portfolioTable.isInitialized || !::shareholder.isInitialized) return
        val who = shareholder.selectedItem?.toString() ?: return
        val latest = db.portfolioLatestRows(who)
        renderPortfolioForDate(latest.first)
    }

    private fun renderPortfolioForDate(date: String) {
        portfolioTable.removeAllViews()
        portfolioDate.text = if (date.isBlank()) "پرتفوی واردشده: اطلاعاتی ثبت نشده است" else "پرتفوی واردشده — تاریخ: ${toPersianDate(date)}"
        val headers = listOf("نام نماد", "تعداد دارایی", "تعداد سهام جایزه", "تعداد حق تقدم", "آخرین قیمت", "قیمت میانگین کل خرید با لحاظ کارمزد", "ارزش نماد")
        val header = TableRow(this)
        headers.forEach { header.addView(cell(it, true)) }
        portfolioTable.addView(header)
        if (date.isBlank()) {
            val row = TableRow(this)
            row.addView(cell("برای این سهامدار پرتفویی وارد نشده است.", false))
            portfolioTable.addView(row)
            return
        }
        val rows = db.portfolioRows(shareholder.selectedItem.toString(), date)
        if (rows.isEmpty()) {
            val row = TableRow(this)
            row.addView(cell("برای این تاریخ پرتفویی ثبت نشده است.", false))
            portfolioTable.addView(row)
            return
        }
        rows.forEach { r ->
            val qty = r[1].toLongOrNull() ?: 0L
            val bonus = r[2].toLongOrNull() ?: 0L
            val right = r[3].toLongOrNull() ?: 0L
            val last = r[4].toLongOrNull() ?: 0L
            val value = r[6].toDoubleOrNull()?.toLong() ?: (qty * last)
            val row = TableRow(this)
            row.addView(cell(r[0], false))
            row.addView(countCell(qty, false))
            row.addView(countCell(bonus, false))
            row.addView(countCell(right, false))
            row.addView(financialCell(last, false))
            row.addView(financialCell(r[5].toDoubleOrNull()?.toLong() ?: 0L, false))
            row.addView(financialCell(value, false))
            portfolioTable.addView(row)
        }
    }

    private fun showPortfolioHistory() {
        val who = shareholder.selectedItem?.toString() ?: return
        val dates = db.portfolioSnapshotDates(who)
        if (dates.isEmpty()) {
            status.text = "برای این سهامدار سابقه پرتفویی ثبت نشده است."
            return
        }
        val labels = dates.map { toPersianDate(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("سوابق پرتفوی — $who")
            .setItems(labels) { _, which -> renderPortfolioForDate(dates[which]) }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun refreshSummary() {
        val who = shareholder.selectedItem?.toString() ?: return
        val latestDate = db.latestPortfolioDate(who)
        if (latestDate.isBlank()) {
            summaryHeading.text = "خلاصه وضعیت مالی — اطلاعاتی ثبت نشده است"
            summaryAssets.text = "—"
            summaryPortfolio.text = "—"
            summaryPl.text = "—"
            summaryDailyPl.text = "—"
            summaryCash.text = "—"
            return
        }
        summaryHeading.text = "خلاصه وضعیت مالی — آخرین اطلاعات: ${toPersianDate(latestDate)}"
        val p = db.portfolioSummary(who, latestDate)
        val cash = db.cashBalance(who, latestDate)
        val portfolioValue = p[0]
        // ارزش کل دارایی = مانده نقدی + ارزش کل پرتفوی؛ سهام جایزه تأییدنشده مستثناست.
        val totalAssets = cash + portfolioValue
        val portfolioPl = p.getOrNull(5) ?: 0L
        val dailyPl = db.importedDailyPl(who, latestDate)
        summaryAssets.text = money(totalAssets)
        summaryPortfolio.text = money(portfolioValue)
        summaryPl.text = money(portfolioPl)
        summaryDailyPl.text = money(dailyPl)
        summaryCash.text = money(cash)
        listOf(summaryPl, summaryDailyPl).forEach { valueView ->
            val value = if (valueView === summaryPl) portfolioPl else dailyPl
            valueView.setTextColor(when { value > 0L -> Color.rgb(0, 128, 0); value < 0L -> Color.rgb(200, 0, 0); else -> Color.BLACK })
        }
    }

    private fun cell(text: String, header: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = if (header) 12f else 11f
        setTypeface(null, if (header) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(Color.BLACK)
        gravity = Gravity.CENTER
        setPadding(12, 14, 12, 14)
        minWidth = if (header) 120 else 105
        setBackgroundResource(android.R.drawable.editbox_background)
    }

    private fun financialCell(value: Long, header: Boolean): TextView = cell(money(value), header).apply {
        setTextColor(when { value > 0L -> Color.rgb(0, 128, 0); value < 0L -> Color.rgb(200, 0, 0); else -> Color.BLACK })
    }

    private fun countCell(value: Long, header: Boolean): TextView = cell(toPersianDigits(String.format(Locale.US, "%,d", value)), header).apply {
        setTextColor(when { value > 0L -> Color.rgb(0, 128, 0); value < 0L -> Color.rgb(200, 0, 0); else -> Color.BLACK })
    }

    private fun openDayDialog(date: String) {
        val who = shareholder.selectedItem?.toString() ?: return
        val report = db.dailyReport(who, date)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 6, 18, 0)
        }
        val formScroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        formScroll.addView(box)

        box.addView(TextView(this).apply {
            text = "ورود اطلاعات دستی روز ${toPersianDate(date)}"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        })

        box.addView(TextView(this).apply {
            text = "تاریخ"
            textSize = 12f
        })
        box.addView(TextView(this).apply {
            text = toPersianDate(date)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(12, 10, 12, 10)
            setBackgroundResource(android.R.drawable.editbox_background)
        })

        fun manualField(label: String, value: Long, hint: String): EditText {
            box.addView(TextView(this).apply {
                text = label
                textSize = 12f
                setPadding(0, 6, 0, 0)
            })
            return edit(if (value == 0L) "" else value.toString()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                this.hint = hint
                box.addView(this)
            }
        }

        // ارزش سهام جایزه از Snapshot واردشده از Excel خوانده می‌شود و دستی نیست.
        val cashEdit = manualField("موجودی نقدی (ریال)", db.cashBalance(who, date), "موجودی نقدی همان روز")
        val bonusValue = db.portfolioSummary(who, date)[3]
        box.addView(TextView(this).apply {
            text = "ارزش سهام جایزه (ریال): ${money(bonusValue)}"
            textSize = 13f
            setPadding(0, 8, 0, 8)
        })
        val depositEdit = manualField("واریز (ریال)", report[4], "مبلغ واریز")
        val withdrawalEdit = manualField("برداشت (ریال)", report[5], "مبلغ برداشت")

        box.addView(TextView(this).apply {
            text = "خرید و فروش این روز از فایل Excel تاریخچه سفارشات خوانده می‌شود و در این فرم به‌صورت دستی وارد نمی‌شود."
            textSize = 12f
            setPadding(0, 12, 0, 6)
        })

        content.addView(
            formScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        lateinit var dialog: AlertDialog
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        val cancel = Button(this).apply {
            text = "انصراف"
            setOnClickListener { dialog.dismiss() }
        }
        val save = Button(this).apply {
            text = "ثبت"
            setOnClickListener {
                val cash = normalizeDigits(cashEdit.text.toString())
                    .replace(",", "").replace("٬", "").toLongOrNull() ?: 0L
                val deposit = normalizeDigits(depositEdit.text.toString())
                    .replace(",", "").replace("٬", "").toLongOrNull() ?: 0L
                val withdrawal = normalizeDigits(withdrawalEdit.text.toString())
                    .replace(",", "").replace("٬", "").toLongOrNull() ?: 0L

                // مقادیر محاسباتی در این فرم ذخیره/ویرایش نمی‌شوند؛
                // جدول اصلی آنها را از Snapshot پرتفوی و سفارشات همان تاریخ محاسبه می‌کند.
                val snapshot = db.portfolioSummary(who, date)
                val portfolioValue = snapshot[0]
                // سود و زیان پرتفوی از ستون «سود و زیان فعلی» کارگزاری می‌آید.
                val portfolioPl = snapshot.getOrNull(5) ?: 0L
                // سود و زیان روز نیز فقط از ستون «سود و زیان امروز» همان روز می‌آید.
                val dailyPl = if (db.hasPortfolioSnapshot(who, date)) db.importedDailyPl(who, date) else db.dailyReport(who, date)[3]
                // سهام جایزه تأییدنشده در ارزش دارایی فعلی لحاظ نمی‌شود.
                val bonusValue = 0L
                val totalAssets = cash + portfolioValue

                db.saveDailyReport(
                    who,
                    date,
                    cash,
                    bonusValue,
                    totalAssets,
                    portfolioValue,
                    portfolioPl,
                    dailyPl,
                    deposit,
                    withdrawal
                )
                status.text = "اطلاعات دستی روز ${toPersianDate(date)} ثبت شد."
                refreshSummary()
                refreshTable()
                dialog.dismiss()
            }
        }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(save, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(buttons)

        val deleteReport = Button(this).apply {
            text = "حذف اطلاعات روز"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("حذف اطلاعات روز")
                    .setMessage("موجودی نقدی، واریز و برداشت این روز حذف شود؟")
                    .setNegativeButton("انصراف", null)
                    .setPositiveButton("حذف") { _, _ ->
                        db.deleteDailyReport(who, date)
                        status.text = "اطلاعات دستی روز ${toPersianDate(date)} حذف شد."
                        refreshSummary()
                        refreshTable()
                        dialog.dismiss()
                    }.show()
            }
        }
        content.addView(deleteReport)

        if (db.hasPortfolioSnapshot(who, date)) {
            val deletePortfolio = Button(this).apply {
                text = "حذف پرتفوی این روز"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("حذف پرتفوی")
                        .setMessage("پرتفوی Excel این تاریخ حذف شود؟")
                        .setNegativeButton("انصراف", null)
                        .setPositiveButton("حذف") { _, _ ->
                            db.deletePortfolioSnapshot(who, date)
                            status.text = "پرتفوی ${toPersianDate(date)} حذف شد."
                            refreshSummary()
                            refreshTable()
                            refreshPortfolioTable()
                            dialog.dismiss()
                        }.show()
                }
            }
            content.addView(deletePortfolio)
        }

        dialog = AlertDialog.Builder(this)
            .setTitle("فرم ورود دستی — ${toPersianDate(date)}")
            .setView(content)
            .create()
        dialog.show()
    }

    private fun openTradeSubform(tradeType: String, selectedDate: String) {
        val shareholderName = shareholder.selectedItem.toString()
        val existing = db.transactionsFor(shareholderName, selectedDate, tradeType)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 4, 12, 0) }
        val formScroll = ScrollView(this)
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(TextView(this).apply {
            text = "$tradeType — معاملات ${toPersianDate(selectedDate)}"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        })
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(rows)
        val subStatus = TextView(this).apply { textSize = 12f; setTextColor(Color.RED); setPadding(0, 6, 0, 6) }
        form.addView(subStatus)

        fun addRow(symbolValue: String = "", qtyValue: String = "", priceValue: String = "") {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 3, 0, 3) }
            val symbol = EditText(this).apply { hint = "نماد"; setText(symbolValue); inputType = InputType.TYPE_CLASS_TEXT }
            val qty = EditText(this).apply { hint = "تعداد"; setText(qtyValue); inputType = InputType.TYPE_CLASS_NUMBER }
            val price = EditText(this).apply { hint = "قیمت"; setText(priceValue); inputType = InputType.TYPE_CLASS_NUMBER }
            val remove = Button(this).apply { text = "حذف" }
            row.addView(symbol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f))
            row.addView(qty, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(price, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(remove, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, .65f))
            remove.setOnClickListener { rows.removeView(row) }
            rows.addView(row)
        }

        if (existing.isEmpty()) addRow() else existing.forEach { addRow(it[1], it[2], it[3]) }
        form.addView(Button(this).apply { text = "+ افزودن ردیف معامله"; setOnClickListener { addRow() } })
        formScroll.addView(form)
        content.addView(formScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        lateinit var dialog: AlertDialog
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 8, 0, 8) }
        val cancel = Button(this).apply { text = "انصراف"; setOnClickListener { dialog.dismiss() } }
        val save = Button(this).apply {
            text = "ثبت معاملات"
            setOnClickListener {
                val trades = mutableListOf<Triple<String, Long, Long>>()
                for (i in 0 until rows.childCount) {
                    val r = rows.getChildAt(i) as LinearLayout
                    val s = (r.getChildAt(0) as EditText).text.toString().trim().uppercase(Locale.US)
                    val q = (r.getChildAt(1) as EditText).text.toString().trim().toLongOrNull()
                    val p = (r.getChildAt(2) as EditText).text.toString().trim().toLongOrNull()
                    if (s.isBlank() || q == null || q <= 0L || p == null || p < 0L) {
                        subStatus.text = "اطلاعات این ردیف کامل نیست. نماد، تعداد و قیمت را وارد کنید."
                        return@setOnClickListener
                    }
                    trades += Triple(s, q, p)
                }
                val error = db.replaceTrades(shareholderName, selectedDate, tradeType, trades)
                if (error != null) {
                    subStatus.text = error
                    return@setOnClickListener
                }
                status.text = "${toPersianDigits(trades.size.toString())} معامله $tradeType برای ${toPersianDate(selectedDate)} ثبت شد."
                refreshTable()
                dialog.dismiss()
            }
        }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(save, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(buttons)

        val deleteButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        val deleteReport = Button(this).apply {
            text = "حذف اطلاعات روز"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("حذف اطلاعات روز")
                    .setMessage("اطلاعات دستی این روز شامل موجودی نقدی، واریز و برداشت حذف شود؟")
                    .setNegativeButton("انصراف", null)
                    .setPositiveButton("حذف") { _, _ ->
                        db.deleteDailyReport(shareholderName, selectedDate)
                        status.text = "اطلاعات روز ${toPersianDate(selectedDate)} حذف شد."
                        refreshTable()
                        dialog.dismiss()
                    }.show()
            }
        }
        deleteButtons.addView(deleteReport, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (db.hasPortfolioSnapshot(shareholderName, selectedDate)) {
            val deletePortfolio = Button(this).apply {
                text = "حذف پرتفوی این روز"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("حذف پرتفوی")
                        .setMessage("پرتفوی Excel و اطلاعات محاسباتی این تاریخ حذف شود؟")
                        .setNegativeButton("انصراف", null)
                        .setPositiveButton("حذف") { _, _ ->
                            db.deletePortfolioSnapshot(shareholderName, selectedDate)
                            status.text = "پرتفوی ${toPersianDate(selectedDate)} حذف شد."
                            refreshTable()
                            refreshPortfolioTable()
                            dialog.dismiss()
                        }.show()
                }
            }
            deleteButtons.addView(deletePortfolio, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(deleteButtons)
        dialog = AlertDialog.Builder(this).setTitle("Subform $tradeType — ${toPersianDate(selectedDate)}").setView(content).create()
        dialog.show()
    }

    companion object {
        private const val REQUEST_PORTFOLIO = 1001
        private const val REQUEST_ORDERS = 1002
        private const val REQUEST_SHAREHOLDER_PHOTO_ADD = 1003
        private const val REQUEST_SHAREHOLDER_PHOTO_EDIT = 1004
    }

    private fun pickExcel(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        try {
            val selectedUri = data.data!!
            if (requestCode == REQUEST_SHAREHOLDER_PHOTO_ADD || requestCode == REQUEST_SHAREHOLDER_PHOTO_EDIT) {
                pendingPhotoUri = selectedUri
                try { contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                status.text = "عکس انتخاب شد. برای ثبت، دکمه ذخیره را بزنید."
                return
            }
            val takeFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (takeFlags != 0) {
                try { contentResolver.takePersistableUriPermission(selectedUri, takeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            }
            val rows = XlsxReader.read(this, selectedUri)
            if (rows.size < 2) throw IllegalArgumentException("فایل Excel داده‌ای ندارد.")
            if (requestCode == REQUEST_PORTFOLIO) importPortfolio(rows, data.data!!) else importOrders(rows, data.data!!)
        } catch (e: Exception) {
            status.text = "خطا در خواندن Excel: ${e.message ?: "فایل نامعتبر است."}"
        }
    }

    private fun normalizeDigits(v: String): String = v.map {
        when (it) { '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'; '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'; else -> it }
    }.joinToString("")

    private fun normalizeDate(v: String): String {
        val s = normalizeDigits(v.trim()).replace('/', '-').replace('.', '-')
        if (Regex("\\d{8}").matches(s)) {
            val y = s.substring(0, 4).toInt()
            val m = s.substring(4, 6).toInt()
            val d = s.substring(6, 8).toInt()
            return if (y >= 1900) formatDate(gregorianToJalali(y, m, d)) else String.format("%04d-%02d-%02d", y, m, d)
        }
        if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(s)) {
            val y = s.substring(0, 4).toInt()
            return if (y >= 1900) {
                val m = s.substring(5, 7).toInt(); val d = s.substring(8, 10).toInt()
                formatDate(gregorianToJalali(y, m, d))
            } else s
        }
        return s
    }

    private fun headerIndex(header: List<String>, name: String): Int = header.indexOfFirst { it.trim() == name.trim() }

    private fun importPortfolio(rows: List<List<String>>, uri: Uri) {
        val h = rows.first().map { it.trim() }
        val symbol = headerIndex(h, "نام نماد")
        val qty = headerIndex(h, "تعداد دارایی")
        val bonus = headerIndex(h, "تعداد سهام جایزه")
        val right = headerIndex(h, "تعداد حق تقدم")
        val last = headerIndex(h, "آخرین قیمت")
        val avg = headerIndex(h, "قیمت میانگین کل خرید با لحاظ کارمزد")
        val currentValue = headerIndex(h, "ارزش فعلی")
        val currentPl = headerIndex(h, "سود و زیان فعلی")
        val bonusValue = headerIndex(h, "ارزش سهام جایزه")
        val dailyPl = headerIndex(h, "سود و زیان امروز")
        val optionalDate = headerIndex(h, "تاریخ آخرین تراکنش و رویداد")
        if (listOf(symbol, qty, bonus, right, last, avg, currentValue, currentPl, dailyPl).any { it < 0 }) throw IllegalArgumentException("ستون‌های لازم فایل پرتفوی پیدا نشد. فایل باید شامل ارزش فعلی، سود و زیان فعلی و سود و زیان امروز نیز باشد.")
        val parsed = rows.drop(1).filter { it.getOrNull(symbol)?.isNotBlank() == true }.map { r ->
            arrayOf(r[symbol].trim(), cleanNumber(r[qty]), cleanNumber(r[bonus]), cleanNumber(r[right]), cleanNumber(r[last]), cleanNumber(r[avg]),
                cleanNumber(r[currentValue]),
                cleanNumber(r[currentPl]),
                if (bonusValue >= 0) cleanNumber(r[bonusValue]) else "0",
                cleanNumber(r[dailyPl]))
        }
        if (parsed.isEmpty()) throw IllegalArgumentException("هیچ نمادی در فایل پرتفوی پیدا نشد.")
        val fileDates = if (optionalDate >= 0) rows.drop(1).mapNotNull { it.getOrNull(optionalDate)?.takeIf { v -> v.isNotBlank() }?.let { normalizeDate(it) } }.distinct() else emptyList()
        val fileDate = if (fileDates.size == 1) fileDates.first() else ""
        val dateEdit = edit(if (fileDate.isNotBlank()) toPersianDate(fileDate) else toPersianDate(today()))
        dateEdit.hint = "۱۴۰۵/۰۹/۰۱"
        AlertDialog.Builder(this)
            .setTitle("تاریخ ثبت پرتفوی")
            .setMessage("اطلاعات پرتفوی و «ارزش سهام جایزه» مستقیماً از فایل Excel خوانده می‌شود. تاریخ Snapshot را مشخص کنید:")
            .setView(dateEdit)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ثبت پرتفوی") { _, _ ->
                val snapshotDate = normalizeDate(dateEdit.text.toString())
                if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(snapshotDate)) {
                    status.text = "تاریخ را به شکل ۱۴۰۵/۰۹/۰۱ وارد کنید."
                    return@setPositiveButton
                }
                db.savePortfolioSnapshot(shareholder.selectedItem.toString(), snapshotDate, parsed, "Portfolio")
                status.text = "پرتفوی ${toPersianDigits(parsed.size.toString())} نماد برای تاریخ ${toPersianDate(snapshotDate)} ثبت شد."
                refreshSummary()
                refreshPortfolioTable()
                refreshTable()
            }.show()
    }

    private fun importOrders(rows: List<List<String>>, uri: Uri) {
        val h = rows.first()
        val date = headerIndex(h, "تاریخ")
        val time = headerIndex(h, "ساعت")
        val side = headerIndex(h, "سمت سفارش")
        val symbol = headerIndex(h, "نماد")
        val price = headerIndex(h, "قیمت")
        val doneQty = headerIndex(h, "حجم انجام شده")
        val statusCol = headerIndex(h, "وضعیت")
        if (listOf(date,time,side,symbol,price,doneQty,statusCol).any { it < 0 }) throw IllegalArgumentException("ستون‌های لازم فایل سفارشات پیدا نشد.")
        val parsed = rows.drop(1).filter {
            it.getOrNull(statusCol)?.trim() == "انجام شده" && (cleanNumber(it.getOrNull(doneQty) ?: "").toLongOrNull() ?: 0L) > 0
        }.map { r ->
            arrayOf(normalizeDate(r[date]), r[time].trim(), r[side].trim(), r[symbol].trim(), cleanNumber(r[doneQty]), cleanNumber(r[price]))
        }
        if (parsed.isEmpty()) { status.text = "هیچ معامله انجام‌شده‌ای در فایل سفارشات پیدا نشد."; return }
        db.saveImportedTransactions(shareholder.selectedItem.toString(), parsed, "Orders")
        val dates = parsed.map { it[0] }.distinct().sorted()
        status.text = "${toPersianDigits(parsed.size.toString())} معامله انجام‌شده از ${toPersianDate(dates.first())} تا ${toPersianDate(dates.last())} وارد شد."
        refreshTable()
    }

    private fun cleanNumber(v: String): String = normalizeDigits(v.trim()).replace(",", "").replace("٬", "").replace("٫", ".")

    private fun renameShareholder() {
        val oldName = shareholder.selectedItem?.toString() ?: return
        val e = edit(oldName)
        AlertDialog.Builder(this).setTitle("ویرایش نام سهامدار").setView(e).setNegativeButton("انصراف", null).setPositiveButton("ذخیره") { _, _ ->
            val newName = e.text.toString().trim()
            if (newName.isBlank()) { status.text = "نام سهامدار نمی‌تواند خالی باشد."; return@setPositiveButton }
            if (!db.renameShareholder(oldName, newName)) { status.text = "این نام قبلاً وجود دارد یا تغییری انجام نشده است."; return@setPositiveButton }
            shareholder.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, db.shareholders())
            status.text = "نام سهامدار به «$newName» تغییر کرد و سوابق قبلی حفظ شد."
            refreshTable()
        }.show()
    }

    private fun refreshShareholderPhoto() {
        if (!::shareholderPhoto.isInitialized || !::shareholder.isInitialized || shareholder.selectedItem == null) return
        val uriText = db.shareholderPhoto(shareholder.selectedItem.toString())
        try { shareholderPhoto.setImageURI(if (uriText.isNullOrBlank()) null else Uri.parse(uriText)) } catch (_: Exception) { shareholderPhoto.setImageResource(android.R.drawable.ic_menu_myplaces) }
    }

    private fun addShareholderDialog() {
        val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(18,0,18,0) }
        val name = edit(""); name.hint="نام و نام خانوادگی"; box.addView(name)
        val pick = Button(this).apply { text="انتخاب عکس" }; box.addView(pick)
        var photo: Uri? = null
        pick.setOnClickListener { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="image/*"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }, REQUEST_SHAREHOLDER_PHOTO_ADD) }
        pendingPhotoUri = null
        AlertDialog.Builder(this).setTitle("افزودن سهامدار").setView(box).setNegativeButton("انصراف",null).setPositiveButton("ذخیره") { _, _ ->
            val uri = pendingPhotoUri?.toString(); if (!db.addShareholder(name.text.toString(), uri)) { status.text="نام سهامدار خالی است یا قبلاً ثبت شده."; return@setPositiveButton }
            shareholder.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,db.shareholders()); shareholder.setSelection(db.shareholders().indexOf(name.text.toString().trim()).coerceAtLeast(0)); refreshShareholderPhoto(); status.text="سهامدار جدید اضافه شد."; pendingPhotoUri=null
        }.show()
    }

    private var pendingPhotoUri: Uri? = null
    private fun editShareholderDialog() {
        val who=shareholder.selectedItem?.toString() ?: return
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,0,18,0)}
        val name=edit(who); box.addView(name)
        val pick=Button(this).apply{text="تغییر عکس"}; box.addView(pick)
        pick.setOnClickListener{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*";addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},REQUEST_SHAREHOLDER_PHOTO_EDIT)}
        AlertDialog.Builder(this).setTitle("ویرایش سهامدار").setView(box).setNegativeButton("انصراف",null).setPositiveButton("ذخیره") { _, _ ->
            val n=name.text.toString().trim(); if(n.isBlank()){status.text="نام سهامدار نمی‌تواند خالی باشد.";return@setPositiveButton}
            if(n!=who && !db.renameShareholder(who,n)){status.text="این نام قبلاً وجود دارد.";return@setPositiveButton}
            if(pendingPhotoUri!=null) db.setShareholderPhoto(n,pendingPhotoUri.toString())
            shareholder.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,db.shareholders()); shareholder.setSelection(db.shareholders().indexOf(n).coerceAtLeast(0)); refreshShareholderPhoto(); pendingPhotoUri=null; status.text="اطلاعات سهامدار ویرایش شد."
        }.show()
    }

    private fun deactivateShareholderDialog() {
        val who=shareholder.selectedItem?.toString() ?: return
        AlertDialog.Builder(this).setTitle("حذف سهامدار").setMessage("سهامدار «$who» از فهرست فعال حذف شود؟ سوابق تاریخی حفظ می‌شوند.").setNegativeButton("انصراف",null).setPositiveButton("حذف") { _, _ ->
            db.deactivateShareholder(who); shareholder.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,db.shareholders()); refreshShareholderPhoto(); status.text="سهامدار حذف شد و سوابق او حفظ شد."
        }.show()
    }

    private fun addField(root: LinearLayout, label: String, view: View) { root.addView(TextView(this).apply { text = label; textSize = 13f }); root.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
    private fun edit(value: String) = EditText(this).apply { setText(value); inputType = InputType.TYPE_CLASS_TEXT }
    private fun money(v: Long): String = if (v == 0L) "—" else toPersianDigits(String.format(Locale.US, "%,d", v)) + " ریال"
    private fun currentMonth(): String { val j = gregorianToJalali(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH)+1, Calendar.getInstance().get(Calendar.DAY_OF_MONTH)); return String.format("%04d-%02d", j[0], j[1]) }
    private fun today(): String { val c = Calendar.getInstance(); return formatDate(gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH)+1, c.get(Calendar.DAY_OF_MONTH))) }

    private fun toPersianDigits(v: String): String = v.map { ch -> when (ch) { '0' -> '۰'; '1' -> '۱'; '2' -> '۲'; '3' -> '۳'; '4' -> '۴'; '5' -> '۵'; '6' -> '۶'; '7' -> '۷'; '8' -> '۸'; '9' -> '۹'; else -> ch } }.joinToString("")
    private fun toPersianDate(v: String): String = toPersianDigits(v.replace('-', '/'))
    private fun formatDate(j: IntArray): String = String.format(Locale.US, "%04d-%02d-%02d", j[0], j[1], j[2])
    private fun jalaliMonthDays(year: Int, month: Int): Int = when { month in 1..6 -> 31; month in 7..11 -> 30; else -> if (isJalaliLeap(year)) 30 else 29 }
    private fun isJalaliLeap(year: Int): Boolean { val r = year % 33; return r in listOf(1,5,9,13,17,22,26,30) }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        var y = gy - 1600; var m = gm - 1; var d = gd - 1
        val gdm = intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31)
        var gDayNo = 365*y + (y+3)/4 - (y+99)/100 + (y+399)/400
        for (i in 0 until m) gDayNo += gdm[i]
        if (m > 1 && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) gDayNo++
        gDayNo += d
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053; jDayNo %= 12053
        var jy = 979 + 33*jNp + 4*(jDayNo/1461); jDayNo %= 1461
        if (jDayNo >= 366) { jy += (jDayNo-1)/365; jDayNo = (jDayNo-1)%365 }
        val jm: Int; val jd: Int
        if (jDayNo < 186) { jm = 1 + jDayNo/31; jd = 1 + jDayNo%31 } else { jm = 7 + (jDayNo-186)/30; jd = 1 + (jDayNo-186)%30 }
        return intArrayOf(jy, jm, jd)
    }

    private fun exportCsv() {
        val who = shareholder.selectedItem.toString()
        val csv = buildString { append("سهامدار,نماد,نوع,تعداد,قیمت,تاریخ\n"); db.allFor(who).forEach { r -> append(r.joinToString(",") { v -> "\"" + v.replace("\"", "\"\"") + "\"" }).append("\n") } }
        val v = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, "Report-saham-$who-${today()}.csv"); put(MediaStore.Downloads.MIME_TYPE, "text/csv"); put(MediaStore.Downloads.RELATIVE_PATH, "Download") }
        contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)?.let { uri -> contentResolver.openOutputStream(uri)?.use { it.write(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte())); it.write(csv.toByteArray(Charsets.UTF_8)) }; status.text = "گزارش CSV سهامدار $who ذخیره شد." }
    }

    private fun exportPdf() {
        val who = shareholder.selectedItem?.toString() ?: return
        val ym = normalizeDigits(month.text.toString().trim()).replace('/', '-')
        if (!Regex("\\d{4}-\\d{2}").matches(ym)) { status.text = "ماه گزارش معتبر نیست."; return }
        val days = jalaliMonthDays(ym.substring(0,4).toInt(), ym.substring(5,7).toInt())
        val rows = mutableListOf<Array<String>>()
        for (day in 1..days) {
            val date = "$ym-${day.toString().padStart(2,'0')}"
            val report = db.dailyReport(who, date)
            val totals = db.dailyTotals(who, date)
            val cash = db.cashBalance(who, date)
            val has = db.hasPortfolioSnapshot(who, date)
            val p = if (has) db.portfolioSummary(who, date) else LongArray(5)
            val daily = if (has) db.importedDailyPl(who,date) else 0L
            rows += arrayOf(toPersianDate(date), money(cash), money(daily), money(report[4]), money(report[5]), money(totals.first), money(totals.second), money(p[0] + cash))
        }
        val doc = PdfDocument()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f }
        val bold = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f; typeface = Typeface.DEFAULT_BOLD }
        val green = Color.rgb(0,128,0); val red = Color.rgb(200,0,0)
        var pageNo = 0
        var index = 0
        fun newPage(): PdfDocument.Page { pageNo++; return doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create()) }
        var page = newPage(); var canvas = page.canvas; var y = 36f
        fun footer() { paint.textSize = 8f; canvas.drawText("حقوق مؤلف: @izadimbi", 30f, 820f, paint); canvas.drawText("صفحه ${toPersianDigits(pageNo.toString())}", 500f, 820f, paint); paint.textSize = 10f }
        // عکس سهامدار در کنار نام و با اندازه خوانا در سربرگ گزارش
        val photoText = db.shareholderPhoto(who)
        var headerRight = 565f
        if (!photoText.isNullOrBlank()) {
            try {
                val bmp = BitmapFactory.decodeStream(contentResolver.openInputStream(Uri.parse(photoText)))
                if (bmp != null) {
                    val photoSize = 100f
                    val src = Rect(0, 0, bmp.width, bmp.height)
                    val dst = RectF(headerRight - photoSize, y - 8f, headerRight, y + photoSize - 8f)
                    canvas.drawBitmap(bmp, src, dst, paint)
                    headerRight -= photoSize + 16f
                }
            } catch (_: Exception) {}
        }
        val titleX = 30f
        canvas.drawText("گزارش روزانه سهامدار:", titleX, y + 22f, bold)
        canvas.drawText(who, titleX, y + 48f, bold)
        y += 116f
        canvas.drawText("دوره: ${toPersianDigits(ym.replace('-', '/'))}", 30f, y, paint); y += 24f
        val pLatest = db.portfolioLatestRows(who)
        val latestDate = pLatest.first
        val latestSummary = if (latestDate.isBlank()) LongArray(5) else db.portfolioSummary(who, latestDate)
        canvas.drawText("ارزش کل دارایی: ${money(latestSummary[0] + db.cashBalance(who, latestDate))}", 30f, y, paint); y += 16f
        canvas.drawText("ارزش کل پرتفوی: ${money(latestSummary[0])}", 30f, y, paint); y += 16f
        canvas.drawText("سود و زیان پرتفوی: ${money(latestSummary.getOrNull(5) ?: 0L)}", 30f, y, paint); y += 22f
        // table header
        val headers = listOf("تاریخ","نقدی","سود روز","واریز","برداشت","خرید","فروش","دارایی")
        val xs = floatArrayOf(30f,95f,170f,245f,315f,385f,455f,525f)
        headers.forEachIndexed { i,h -> canvas.drawText(h, xs[i], y, bold) }; y += 18f
        val tableStart = y
        val chartData = mutableListOf<Pair<String,Long>>()
        rows.forEach { r ->
            if (y > 760f) { footer(); doc.finishPage(page); page=newPage(); canvas=page.canvas; y=36f; headers.forEachIndexed { i,h -> canvas.drawText(h,xs[i],y,bold) }; y+=18f }
            r.forEachIndexed { i,v -> paint.color = if (i==2 && v.contains("-") ) red else if (i==2 && v != "—") green else Color.BLACK; canvas.drawText(v.take(15), xs[i], y, paint) }
            val asset = normalizeDigits(r[7]).replace(",","").replace("٬","").replace(" ریال","").toLongOrNull() ?: 0L
            chartData += r[0] to asset
            y += 16f
        }
        // chart on a new page for readability
        footer(); doc.finishPage(page)
        page=newPage(); canvas=page.canvas; y=42f
        canvas.drawText("نمودار روند ارزش کل دارایی", 30f, y, bold); y += 24f
        val max = chartData.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
        val baseY = 760f; val topY = 100f; val left = 50f; val right = 560f
        paint.color = Color.DKGRAY; canvas.drawLine(left, baseY, right, baseY, paint); canvas.drawLine(left, topY, left, baseY, paint)
        val step = if (chartData.size <= 1) 0f else (right-left)/(chartData.size-1).toFloat()
        var prevX = left; var prevY = baseY
        chartData.forEachIndexed { i, pair ->
            val x = left + step*i; val yy = baseY - ((pair.second.toDouble()/max.toDouble())*(baseY-topY)).toFloat()
            paint.color = Color.rgb(40,90,160); paint.strokeWidth=2f
            if (i>0) canvas.drawLine(prevX,prevY,x,yy,paint)
            canvas.drawCircle(x,yy,2.5f,paint)
            if (i % 3 == 0 || i == chartData.lastIndex) { paint.color=Color.BLACK; paint.textSize=7f; canvas.drawText(pair.first.takeLast(5), x-12, baseY+14, paint) }
            prevX=x; prevY=yy
        }
        paint.color=Color.BLACK; paint.textSize=9f; canvas.drawText("ارزش دارایی (ریال)", left, topY-12, paint)
        footer(); doc.finishPage(page)
        val v = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME,"Report-saham-$who-${ym}.pdf"); put(MediaStore.Downloads.MIME_TYPE,"application/pdf"); put(MediaStore.Downloads.RELATIVE_PATH,"Download") }
        contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v)?.let { uri -> contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }; status.text="گزارش PDF جدول و نمودار سهامدار $who ذخیره شد." }
        doc.close()
    }

}