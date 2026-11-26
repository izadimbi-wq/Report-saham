package com.izadimbi.reportsaham

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PortfolioDb(context: Context) : SQLiteOpenHelper(context, "portfolio.db", null, 13) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE shareholders (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, photo_uri TEXT, active INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, shareholder TEXT NOT NULL, symbol TEXT NOT NULL, type TEXT NOT NULL, qty INTEGER NOT NULL, price INTEGER NOT NULL, date TEXT NOT NULL)")
        db.execSQL("CREATE TABLE daily_reports (id INTEGER PRIMARY KEY AUTOINCREMENT, shareholder TEXT NOT NULL, date TEXT NOT NULL, cash_balance INTEGER NOT NULL DEFAULT 0, bonus_value INTEGER NOT NULL DEFAULT 0, total_assets INTEGER NOT NULL DEFAULT 0, portfolio_value INTEGER NOT NULL DEFAULT 0, portfolio_pl INTEGER NOT NULL DEFAULT 0, daily_pl INTEGER NOT NULL DEFAULT 0, deposit INTEGER NOT NULL DEFAULT 0, withdrawal INTEGER NOT NULL DEFAULT 0, UNIQUE(shareholder,date))")
        db.execSQL("CREATE INDEX idx_transactions_shareholder_date ON transactions(shareholder,date)")
        db.execSQL("CREATE INDEX idx_daily_reports_shareholder_date ON daily_reports(shareholder,date)")
        createPortfolioTables(db)
        seedShareholders(db)
    }

    private fun seedShareholders(db: SQLiteDatabase) {
        val names = listOf("محمدباقر ایزدی", "سارا ایزدی", "صبا ایزدی", "لیلا طایفه", "برات اله ایزدی", "محمدرضا ایزدی")
        names.forEach { n -> ContentValues().apply { put("name", n) }.also { db.insert("shareholders", null, it) } }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_shareholder_date ON transactions(shareholder,date)")
        if (oldVersion < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS daily_reports (id INTEGER PRIMARY KEY AUTOINCREMENT, shareholder TEXT NOT NULL, date TEXT NOT NULL, cash_balance INTEGER NOT NULL DEFAULT 0, bonus_value INTEGER NOT NULL DEFAULT 0, total_assets INTEGER NOT NULL DEFAULT 0, portfolio_value INTEGER NOT NULL DEFAULT 0, portfolio_pl INTEGER NOT NULL DEFAULT 0, daily_pl INTEGER NOT NULL DEFAULT 0, deposit INTEGER NOT NULL DEFAULT 0, withdrawal INTEGER NOT NULL DEFAULT 0, UNIQUE(shareholder,date))")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_reports_shareholder_date ON daily_reports(shareholder,date)")
        }
        if (oldVersion < 5) createPortfolioTables(db)
        if (oldVersion < 7) {
            // cash_balance is already part of daily_reports in the v4 schema.
            // Keep this migration block empty for compatibility with existing databases.
        }
        if (oldVersion < 8) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_reports_shareholder_date ON daily_reports(shareholder,date)")
        }
        if (oldVersion < 11) {
            val hasPhoto = db.rawQuery("PRAGMA table_info(shareholders)", null).use { c ->
                var found = false
                while (c.moveToNext()) if (c.getString(1) == "photo_uri") { found = true; break }
                found
            }
            if (!hasPhoto) db.execSQL("ALTER TABLE shareholders ADD COLUMN photo_uri TEXT")
            val hasActive = db.rawQuery("PRAGMA table_info(shareholders)", null).use { c ->
                var found = false
                while (c.moveToNext()) if (c.getString(1) == "active") { found = true; break }
                found
            }
            if (!hasActive) db.execSQL("ALTER TABLE shareholders ADD COLUMN active INTEGER NOT NULL DEFAULT 1")
        }
        if (oldVersion < 12) {
            val hasDailyPl = db.rawQuery("PRAGMA table_info(portfolio_snapshots)", null).use { c ->
                var found = false
                while (c.moveToNext()) if (c.getString(1) == "daily_pl") { found = true; break }
                found
            }
            if (!hasDailyPl) db.execSQL("ALTER TABLE portfolio_snapshots ADD COLUMN daily_pl REAL NOT NULL DEFAULT 0")
        }
        if (oldVersion < 13) {
            val hasCurrentPl = db.rawQuery("PRAGMA table_info(portfolio_snapshots)", null).use { c ->
                var found = false
                while (c.moveToNext()) if (c.getString(1) == "current_pl") { found = true; break }
                found
            }
            if (!hasCurrentPl) db.execSQL("ALTER TABLE portfolio_snapshots ADD COLUMN current_pl REAL NOT NULL DEFAULT 0")
        }
        if (oldVersion < 10) {
            val hasBonusValue = db.rawQuery("PRAGMA table_info(daily_reports)", null).use { c ->
                var found = false
                while (c.moveToNext()) if (c.getString(1) == "bonus_value") { found = true; break }
                found
            }
            if (!hasBonusValue) db.execSQL("ALTER TABLE daily_reports ADD COLUMN bonus_value INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 9) {
            val hasBonusValue = db.rawQuery("PRAGMA table_info(portfolio_snapshots)", null).use { c ->
                var found = false
                while (c.moveToNext()) if (c.getString(1) == "bonus_value") { found = true; break }
                found
            }
            if (!hasBonusValue) db.execSQL("ALTER TABLE portfolio_snapshots ADD COLUMN bonus_value REAL NOT NULL DEFAULT 0")
        }
        if (oldVersion < 6) {
            val oldNames = listOf("سهامدار ۱", "سهامدار ۲", "سهامدار ۳", "سهامدار ۴", "سهامدار ۵", "سهامدار ۶")
            val newNames = listOf("محمدباقر ایزدی", "سارا ایزدی", "صبا ایزدی", "لیلا طایفه", "برات اله ایزدی", "محمدرضا ایزدی")
            db.beginTransaction()
            try {
                oldNames.forEachIndexed { i, oldName ->
                    val exists = db.rawQuery("SELECT 1 FROM shareholders WHERE name=?", arrayOf(oldName)).use { it.moveToFirst() }
                    if (exists) {
                        val newName = newNames[i]
                        val conflict = db.rawQuery("SELECT 1 FROM shareholders WHERE name=?", arrayOf(newName)).use { it.moveToFirst() }
                        if (!conflict) {
                            val cv = ContentValues().apply { put("name", newName) }
                            db.update("shareholders", cv, "name=?", arrayOf(oldName))
                            val tv = ContentValues().apply { put("shareholder", newName) }
                            db.update("transactions", tv, "shareholder=?", arrayOf(oldName))
                            db.update("daily_reports", tv, "shareholder=?", arrayOf(oldName))
                            db.update("portfolio_snapshots", tv, "shareholder=?", arrayOf(oldName))
                            db.update("import_batches", tv, "shareholder=?", arrayOf(oldName))
                        }
                    }
                }
                migrateDatesToJalali(db, "transactions")
                migrateDatesToJalali(db, "daily_reports")
                migrateDatesToJalali(db, "portfolio_snapshots")
                migrateDatesToJalali(db, "import_batches")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }

    private fun migrateDatesToJalali(db: SQLiteDatabase, table: String) {
        val rows = mutableListOf<Pair<Long, String>>()
        db.rawQuery("SELECT id,date FROM $table WHERE date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'", null).use { c ->
            while (c.moveToNext()) rows.add(c.getLong(0) to c.getString(1))
        }
        rows.forEach { (id, date) ->
            if (date.substring(0,4).toIntOrNull()?.let { it >= 1900 } == true) {
                val parts = date.split('-')
                val j = gregorianToJalali(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                val cv = ContentValues().apply { put("date", String.format("%04d-%02d-%02d", j[0], j[1], j[2])) }
                db.update(table, cv, "id=?", arrayOf(id.toString()))
            }
        }
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        var y = gy - 1600
        var m = gm - 1
        var d = gd - 1
        val gdm = intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31)
        var gDayNo = 365*y + (y+3)/4 - (y+99)/100 + (y+399)/400
        for (i in 0 until m) gDayNo += gdm[i]
        if (m > 1 && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) gDayNo++
        gDayNo += d
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053
        var jy = 979 + 33*jNp + 4*(jDayNo/1461)
        jDayNo %= 1461
        if (jDayNo >= 366) { jy += (jDayNo-1)/365; jDayNo = (jDayNo-1)%365 }
        val jm: Int
        val jd: Int
        if (jDayNo < 186) { jm = 1 + jDayNo/31; jd = 1 + jDayNo%31 }
        else { jm = 7 + (jDayNo-186)/30; jd = 1 + (jDayNo-186)%30 }
        return intArrayOf(jy, jm, jd)
    }


    private fun createPortfolioTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS portfolio_snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT, shareholder TEXT NOT NULL, date TEXT NOT NULL, symbol TEXT NOT NULL, qty INTEGER NOT NULL DEFAULT 0, bonus_qty INTEGER NOT NULL DEFAULT 0, right_qty INTEGER NOT NULL DEFAULT 0, last_price INTEGER NOT NULL DEFAULT 0, avg_buy_price REAL NOT NULL DEFAULT 0, current_value REAL NOT NULL DEFAULT 0, current_pl REAL NOT NULL DEFAULT 0, bonus_value REAL NOT NULL DEFAULT 0, daily_pl REAL NOT NULL DEFAULT 0, imported_at INTEGER NOT NULL, UNIQUE(shareholder,date,symbol))")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_portfolio_shareholder_date ON portfolio_snapshots(shareholder,date)")
        db.execSQL("CREATE TABLE IF NOT EXISTS import_batches (id INTEGER PRIMARY KEY AUTOINCREMENT, shareholder TEXT NOT NULL, date TEXT NOT NULL, kind TEXT NOT NULL, file_name TEXT, imported_at INTEGER NOT NULL, UNIQUE(shareholder,date,kind))")
    }

    fun savePortfolioSnapshot(shareholder: String, date: String, rows: List<Array<String>>, fileName: String?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("portfolio_snapshots", "shareholder=? AND date=?", arrayOf(shareholder,date))
            rows.forEach { r ->
                val v = ContentValues().apply {
                    put("shareholder", shareholder); put("date", date); put("symbol", r[0]); put("qty", r[1].toLongOrNull() ?: 0L)
                    put("bonus_qty", r[2].toLongOrNull() ?: 0L); put("right_qty", r[3].toLongOrNull() ?: 0L)
                    put("last_price", r[4].toLongOrNull() ?: 0L); put("avg_buy_price", r[5].toDoubleOrNull() ?: 0.0)
                    val totalQty = (r[1].toLongOrNull() ?: 0L)
                    // ارزش فعلی و سود و زیان فعلی مستقیماً از خروجی کارگزاری ذخیره می‌شوند؛
                    // محاسبه مجدد با qty×price یا qty×avg_buy_price باعث اختلاف با کارگزاری می‌شود.
                    put("current_value", r.getOrNull(6)?.toDoubleOrNull() ?: (totalQty.toDouble() * (r[4].toLongOrNull() ?: 0L).toDouble()))
                    put("current_pl", r.getOrNull(7)?.toDoubleOrNull() ?: 0.0)
                    put("bonus_value", r.getOrNull(8)?.toDoubleOrNull() ?: 0.0)
                    put("daily_pl", r.getOrNull(9)?.toDoubleOrNull() ?: 0.0)
                    put("imported_at", System.currentTimeMillis())
                }
                db.insertOrThrow("portfolio_snapshots", null, v)
            }
            val b = ContentValues().apply { put("shareholder",shareholder); put("date",date); put("kind","portfolio"); put("file_name",fileName); put("imported_at",System.currentTimeMillis()) }
            db.insertWithOnConflict("import_batches", null, b, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun portfolioHoldingBefore(shareholder: String, symbol: String, date: String): Long {
        readableDatabase.rawQuery("SELECT qty+bonus_qty+right_qty FROM portfolio_snapshots WHERE shareholder=? AND symbol=? AND date<? ORDER BY date DESC LIMIT 1", arrayOf(shareholder,symbol,date)).use { c -> return if (c.moveToFirst()) c.getLong(0) else 0L }
    }

    fun availableForSale(shareholder: String, symbol: String, date: String): Long {
        var base = 0L
        var baseDate = ""
        readableDatabase.rawQuery("SELECT date,qty+bonus_qty+right_qty FROM portfolio_snapshots WHERE shareholder=? AND symbol=? AND date<? ORDER BY date DESC LIMIT 1", arrayOf(shareholder,symbol,date)).use { c ->
            if (c.moveToFirst()) { baseDate = c.getString(0); base = c.getLong(1) }
        }
        if (baseDate.isBlank()) return currentHolding(shareholder,symbol)
        var net = 0L
        readableDatabase.rawQuery("SELECT COALESCE(SUM(CASE WHEN type='خرید' THEN qty ELSE -qty END),0) FROM transactions WHERE shareholder=? AND symbol=? AND date>? AND date<?", arrayOf(shareholder,symbol,baseDate,date)).use { c -> if (c.moveToFirst()) net=c.getLong(0) }
        readableDatabase.rawQuery("SELECT COALESCE(SUM(qty),0) FROM transactions WHERE shareholder=? AND symbol=? AND date=? AND type='خرید'", arrayOf(shareholder,symbol,date)).use { c -> if (c.moveToFirst()) net += c.getLong(0) }
        return base + net
    }

    fun portfolioSummary(shareholder: String, date: String): LongArray {
        readableDatabase.rawQuery("SELECT COALESCE(SUM(current_value),0), COALESCE(SUM(qty*avg_buy_price),0), COALESCE(SUM(qty),0), COALESCE(SUM(bonus_value),0), COALESCE(SUM(daily_pl),0), COALESCE(SUM(current_pl),0) FROM portfolio_snapshots WHERE shareholder=? AND date=?", arrayOf(shareholder,date)).use { c ->
            if (c.moveToFirst()) return longArrayOf(c.getDouble(0).toLong(), c.getDouble(1).toLong(), c.getLong(2), c.getDouble(3).toLong(), c.getDouble(4).toLong(), c.getDouble(5).toLong())
        }
        return LongArray(3)
    }


    fun portfolioRows(shareholder: String, date: String): List<Array<String>> {
        val out = mutableListOf<Array<String>>()
        readableDatabase.rawQuery(
            "SELECT symbol,qty,bonus_qty,right_qty,last_price,avg_buy_price,current_value,current_pl,bonus_value,daily_pl FROM portfolio_snapshots WHERE shareholder=? AND date=? ORDER BY symbol",
            arrayOf(shareholder, date)
        ).use { c ->
            while (c.moveToNext()) out.add(arrayOf(
                c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                c.getString(4), c.getString(5), c.getString(6), c.getString(7)
            ))
        }
        return out
    }

    fun portfolioLatestRows(shareholder: String): Pair<String, List<Array<String>>> {
        val date = latestPortfolioDate(shareholder)
        return date to if (date.isBlank()) emptyList() else portfolioRows(shareholder, date)
    }

    fun hasPortfolioSnapshot(shareholder: String, date: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM portfolio_snapshots WHERE shareholder=? AND date=? LIMIT 1",
            arrayOf(shareholder, date)
        ).use { c -> return c.moveToFirst() }
    }

    fun portfolioSnapshotDates(shareholder: String): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT DISTINCT date FROM portfolio_snapshots WHERE shareholder=? ORDER BY date DESC", arrayOf(shareholder)).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    fun latestPortfolioDate(shareholder: String): String {
        readableDatabase.rawQuery("SELECT date FROM portfolio_snapshots WHERE shareholder=? ORDER BY date DESC LIMIT 1", arrayOf(shareholder)).use { c -> return if (c.moveToFirst()) c.getString(0) else "" }
    }

    fun saveImportedTransactions(shareholder: String, rows: List<Array<String>>, fileName: String?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // تاریخ‌های موجود در فایل دوباره از روی Excel بازسازی می‌شوند؛
            // بنابراین ورود مجدد همان فایل باعث ثبت رکورد تکراری نمی‌شود.
            val dates = rows.map { it[0] }.distinct()
            dates.forEach { d ->
                db.delete("transactions", "shareholder=? AND date=?", arrayOf(shareholder, d))
                db.delete("import_batches", "shareholder=? AND date=? AND kind=?", arrayOf(shareholder, d, "transactions"))
            }
            rows.forEach { r ->
                val date = r[0]
                val type = r[2]
                val symbol = r[3]
                val qty = r[4].toLongOrNull() ?: 0L
                val price = r[5].toLongOrNull() ?: 0L
                if (qty > 0L) {
                    val v = ContentValues().apply {
                        put("shareholder", shareholder)
                        put("symbol", symbol)
                        put("type", type)
                        put("qty", qty)
                        put("price", price)
                        put("date", date)
                    }
                    db.insertOrThrow("transactions", null, v)
                }
            }
            dates.forEach { d ->
                val b = ContentValues().apply {
                    put("shareholder", shareholder)
                    put("date", d)
                    put("kind", "transactions")
                    put("file_name", fileName)
                    put("imported_at", System.currentTimeMillis())
                }
                db.insertWithOnConflict("import_batches", null, b, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun shareholders(): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT name FROM shareholders WHERE active=1 ORDER BY id", null).use { c -> while (c.moveToNext()) out += c.getString(0) }
        return out
    }

    fun addShareholder(name: String, photoUri: String? = null): Boolean {
        val n = name.trim()
        if (n.isBlank()) return false
        val v = ContentValues().apply { put("name", n); if (photoUri != null) put("photo_uri", photoUri); put("active", 1) }
        return try { writableDatabase.insertOrThrow("shareholders", null, v); true } catch (_: Exception) { false }
    }

    fun setShareholderPhoto(name: String, photoUri: String?) {
        val v = ContentValues().apply { if (photoUri == null) putNull("photo_uri") else put("photo_uri", photoUri) }
        writableDatabase.update("shareholders", v, "name=?", arrayOf(name))
    }

    fun shareholderPhoto(name: String): String? {
        readableDatabase.rawQuery("SELECT photo_uri FROM shareholders WHERE name=?", arrayOf(name)).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return c.getString(0)
        }
        return null
    }

    fun deactivateShareholder(name: String): Boolean {
        val v = ContentValues().apply { put("active", 0) }
        return writableDatabase.update("shareholders", v, "name=?", arrayOf(name)) > 0
    }

    fun activateShareholder(name: String): Boolean {
        val v = ContentValues().apply { put("active", 1) }
        return writableDatabase.update("shareholders", v, "name=?", arrayOf(name)) > 0
    }

    fun allShareholdersWithStatus(): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        readableDatabase.rawQuery("SELECT name,active FROM shareholders ORDER BY id", null).use { c -> while (c.moveToNext()) out += c.getString(0) to (c.getInt(1) == 1) }
        return out
    }

    fun renameShareholder(oldName: String, newName: String): Boolean {
        if (newName.isBlank() || oldName == newName) return false
        writableDatabase.beginTransaction()
        return try {
            val check = readableDatabase.rawQuery("SELECT 1 FROM shareholders WHERE name=?", arrayOf(newName.trim()))
            val exists = check.use { it.moveToFirst() }
            if (exists) return false
            val v = ContentValues().apply { put("name", newName.trim()) }
            writableDatabase.update("shareholders", v, "name=?", arrayOf(oldName))
            val tv = ContentValues().apply { put("shareholder", newName.trim()) }
            writableDatabase.update("transactions", tv, "shareholder=?", arrayOf(oldName))
            writableDatabase.update("daily_reports", tv, "shareholder=?", arrayOf(oldName))
            writableDatabase.update("portfolio_snapshots", tv, "shareholder=?", arrayOf(oldName))
            writableDatabase.update("import_batches", tv, "shareholder=?", arrayOf(oldName))
            writableDatabase.setTransactionSuccessful()
            true
        } finally { writableDatabase.endTransaction() }
    }

    fun insert(shareholder: String, symbol: String, type: String, qty: Long, price: Long, date: String) {
        val v = ContentValues().apply { put("shareholder", shareholder); put("symbol", symbol); put("type", type); put("qty", qty); put("price", price); put("date", date) }
        writableDatabase.insertOrThrow("transactions", null, v)
    }

    fun currentHolding(shareholder: String, symbol: String): Long {
        readableDatabase.rawQuery("SELECT COALESCE(SUM(CASE WHEN type='خرید' THEN qty ELSE -qty END),0) FROM transactions WHERE shareholder=? AND symbol=?", arrayOf(shareholder, symbol)).use { c -> return if (c.moveToFirst()) c.getLong(0) else 0L }
    }

    fun dailyTotals(shareholder: String, date: String): Pair<Long, Long> {
        var buy = 0L; var sell = 0L
        readableDatabase.rawQuery("SELECT type, COALESCE(SUM(qty*price),0) FROM transactions WHERE shareholder=? AND date=? GROUP BY type", arrayOf(shareholder,date)).use { c ->
            while (c.moveToNext()) if (c.getString(0) == "خرید") buy = c.getLong(1) else sell = c.getLong(1)
        }
        return buy to sell
    }

    fun saveDailyReport(shareholder: String, date: String, cashBalance: Long, bonusValue: Long, totalAssets: Long, portfolioValue: Long, portfolioPl: Long, dailyPl: Long, deposit: Long, withdrawal: Long) {
        val v = ContentValues().apply {
            put("shareholder", shareholder); put("date", date); put("cash_balance", cashBalance); put("bonus_value", bonusValue); put("total_assets", totalAssets); put("portfolio_value", portfolioValue)
            put("portfolio_pl", portfolioPl); put("daily_pl", dailyPl); put("deposit", deposit); put("withdrawal", withdrawal)
        }
        writableDatabase.insertWithOnConflict("daily_reports", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteDailyReport(shareholder: String, date: String) {
        writableDatabase.delete("daily_reports", "shareholder=? AND date=?", arrayOf(shareholder, date))
    }

    fun deletePortfolioSnapshot(shareholder: String, date: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("portfolio_snapshots", "shareholder=? AND date=?", arrayOf(shareholder, date))
            db.delete("import_batches", "shareholder=? AND date=? AND kind=?", arrayOf(shareholder, date, "portfolio"))
            db.delete("daily_reports", "shareholder=? AND date=?", arrayOf(shareholder, date))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun cashBalance(shareholder: String, date: String): Long {
        readableDatabase.rawQuery(
            "SELECT cash_balance FROM daily_reports WHERE shareholder=? AND date=? ORDER BY id DESC LIMIT 1",
            arrayOf(shareholder, date)
        ).use { c -> if (c.moveToFirst()) return c.getLong(0) }
        return 0L
    }

    fun importedDailyPl(shareholder: String, date: String): Long {
        readableDatabase.rawQuery("SELECT COALESCE(SUM(daily_pl),0) FROM portfolio_snapshots WHERE shareholder=? AND date=?", arrayOf(shareholder,date)).use { c ->
            return if (c.moveToFirst()) c.getDouble(0).toLong() else 0L
        }
    }

    fun previousPortfolioDate(shareholder: String, date: String): String {
        readableDatabase.rawQuery(
            "SELECT date FROM portfolio_snapshots WHERE shareholder=? AND date<? ORDER BY date DESC LIMIT 1",
            arrayOf(shareholder, date)
        ).use { c -> return if (c.moveToFirst()) c.getString(0) else "" }
    }

    private fun cumulativeTradeTotals(shareholder: String, date: String): Pair<Long, Long> {
        var buy = 0L; var sell = 0L
        readableDatabase.rawQuery(
            "SELECT type, COALESCE(SUM(qty*price),0) FROM transactions WHERE shareholder=? AND date<=? GROUP BY type",
            arrayOf(shareholder, date)
        ).use { c -> while (c.moveToNext()) { if (c.getString(0) == "خرید") buy = c.getLong(1) else sell = c.getLong(1) } }
        return buy to sell
    }

    fun latestDailyReport(shareholder: String): Pair<String, LongArray> {
        readableDatabase.rawQuery(
            "SELECT date,total_assets,portfolio_value,portfolio_pl,daily_pl,deposit,withdrawal,bonus_value FROM daily_reports WHERE shareholder=? ORDER BY date DESC,id DESC LIMIT 1",
            arrayOf(shareholder)
        ).use { c ->
            if (c.moveToFirst()) {
                val date = c.getString(0)
                val values = LongArray(7) { i -> c.getLong(i + 1) }
                return date to values
            }
        }
        return "" to LongArray(7)
    }

    fun dailyReport(shareholder: String, date: String): LongArray {
        readableDatabase.rawQuery("SELECT total_assets,portfolio_value,portfolio_pl,daily_pl,deposit,withdrawal,bonus_value FROM daily_reports WHERE shareholder=? AND date=?", arrayOf(shareholder,date)).use { c ->
            if (c.moveToFirst()) return LongArray(7) { i -> c.getLong(i) }
        }
        return LongArray(7)
    }

    fun transactionsFor(shareholder: String, date: String, type: String): List<Array<String>> {
        val out = mutableListOf<Array<String>>()
        readableDatabase.rawQuery(
            "SELECT id,symbol,qty,price FROM transactions WHERE shareholder=? AND date=? AND type=? ORDER BY id",
            arrayOf(shareholder, date, type)
        ).use { c ->
            while (c.moveToNext()) out.add(arrayOf(c.getString(0), c.getString(1), c.getString(2), c.getString(3)))
        }
        return out
    }

    fun replaceTrades(shareholder: String, date: String, type: String, trades: List<Triple<String, Long, Long>>): String? {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            if (type == "فروش") {
                val holdings = mutableMapOf<String, Long>()
                db.rawQuery(
                    "SELECT symbol, COALESCE(SUM(CASE WHEN type='خرید' THEN qty ELSE -qty END),0) FROM transactions WHERE shareholder=? AND date<>? GROUP BY symbol",
                    arrayOf(shareholder, date)
                ).use { c -> while (c.moveToNext()) holdings[c.getString(0)] = c.getLong(1) }
                db.rawQuery(
                    "SELECT symbol, COALESCE(SUM(qty),0) FROM transactions WHERE shareholder=? AND date=? AND type='خرید' GROUP BY symbol",
                    arrayOf(shareholder, date)
                ).use { c -> while (c.moveToNext()) holdings[c.getString(0)] = (holdings[c.getString(0)] ?: 0L) + c.getLong(1) }
                val proposed = mutableMapOf<String, Long>()
                trades.forEach { (symbol, qty, _) -> proposed[symbol] = (proposed[symbol] ?: 0L) + qty }
                proposed.forEach { (symbol, qty) ->
                    val available = availableForSale(shareholder, symbol, date)
                    if (qty > available) return "فروش $symbol ثبت نشد؛ موجودی قابل فروش: $available سهم"
                }
            }
            db.delete("transactions", "shareholder=? AND date=? AND type=?", arrayOf(shareholder, date, type))
            trades.forEach { (symbol, qty, price) ->
                val v = ContentValues().apply {
                    put("shareholder", shareholder); put("symbol", symbol); put("type", type);
                    put("qty", qty); put("price", price); put("date", date)
                }
                db.insertOrThrow("transactions", null, v)
            }
            db.setTransactionSuccessful()
            null
        } finally { db.endTransaction() }
    }

    fun recentFor(shareholder: String, limit: Int = 100): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT date,symbol,type,qty,price FROM transactions WHERE shareholder=? ORDER BY date DESC,id DESC LIMIT ?", arrayOf(shareholder,limit.toString())).use { c ->
            while (c.moveToNext()) out += "${c.getString(0)} | ${c.getString(1)} | ${c.getString(2)} | ${c.getLong(3)} | ${c.getLong(4)}"
        }
        return out
    }

    fun allFor(shareholder: String): List<Array<String>> {
        val out = mutableListOf<Array<String>>()
        readableDatabase.rawQuery("SELECT shareholder,symbol,type,qty,price,date FROM transactions WHERE shareholder=? ORDER BY date,id", arrayOf(shareholder)).use { c ->
            while (c.moveToNext()) out.add(arrayOf(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5)))
        }
        return out
    }
}
