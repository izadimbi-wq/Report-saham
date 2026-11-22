package com.izadimbi.reportsaham

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PortfolioDb(ctx: Context) :
    SQLiteOpenHelper(ctx, "portfolio.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE shareholders(" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT NOT NULL UNIQUE)"
        )

        db.execSQL(
            "CREATE TABLE transactions(" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "shareholder TEXT, symbol TEXT, type TEXT, " +
            "qty INTEGER, price INTEGER, date TEXT)"
        )

        arrayOf(
            "سهامدار ۱",
            "سهامدار ۲",
            "سهامدار ۳",
            "سهامدار ۴",
            "سهامدار ۵",
            "سهامدار ۶"
        ).forEach {
            db.execSQL(
                "INSERT INTO shareholders(name) VALUES(?)",
                arrayOf(it)
            )
        }
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
    }

    fun shareholders(): List<String> {
        val result = mutableListOf<String>()

        readableDatabase.rawQuery(
            "SELECT name FROM shareholders ORDER BY id",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0))
            }
        }

        return result
    }

    fun insert(
        shareholder: String,
        symbol: String,
        type: String,
        qty: Long,
        price: Long,
        date: String
    ) {
        writableDatabase.execSQL(
            "INSERT INTO transactions(" +
            "shareholder,symbol,type,qty,price,date) " +
            "VALUES(?,?,?,?,?,?)",
            arrayOf(shareholder, symbol, type, qty, price, date)
        )
    }

    fun all(): List<Array<String>> {
        val result = mutableListOf<Array<String>>()

        readableDatabase.rawQuery(
            "SELECT shareholder,symbol,type,qty,price,date " +
            "FROM transactions ORDER BY date DESC,id DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    arrayOf(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5)
                    )
                )
            }
        }

        return result
    }

    fun recent(limit: Int): List<String> =
        all().take(limit).map {
            "${it[5]} | ${it[0]} | ${it[1]} | ${it[2]} | ${it[3]} سهم × ${it[4]}"
        }
}
