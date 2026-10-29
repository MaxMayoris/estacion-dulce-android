package com.estaciondulce.app.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Display-only formatting helpers using Argentine conventions ($1.234 / $1.234,50, "05 abr 19:42").
 * Never use these for values that are parsed back into numbers.
 */
object FormatUtils {

    private val argentinaLocale: Locale = Locale.forLanguageTag("es-AR")
    private val spanishLocale: Locale = Locale.forLanguageTag("es")

    /**
     * Formats an amount as currency, e.g. 1234.0 -> "$1.234" and 1234.5 -> "$1.234,50".
     */
    fun formatCurrency(amount: Double): String = "$" + formatNumber(amount)

    /**
     * Formats a number with thousands separator, showing decimals only when there are cents,
     * e.g. 1234.0 -> "1.234" and 1234.5 -> "1.234,50".
     */
    fun formatNumber(value: Double): String {
        val hasCents = Math.round(value * 100) % 100 != 0L
        val pattern = if (hasCents) "#,##0.00" else "#,##0"
        return DecimalFormat(pattern, DecimalFormatSymbols(argentinaLocale)).format(value)
    }

    /**
     * Formats a number for an editable input field so it can still be parsed with toDouble():
     * whole values have no decimals ("1000"), others keep their usual representation
     * ("%.2f" when twoDecimals is set, otherwise Double.toString()).
     */
    fun formatEditable(value: Double, twoDecimals: Boolean = false): String = when {
        value % 1.0 == 0.0 -> value.toLong().toString()
        twoDecimals -> String.format("%.2f", value)
        else -> value.toString()
    }

    /**
     * Formats a date as "dd mmm HH:mm", adding the year when it is not the current one
     * so that orders from different years are not confused (e.g. "05 abr 2025 19:42").
     */
    fun formatShortDateTime(date: Date): String {
        val pattern = if (isCurrentYear(date)) "dd MMM HH:mm" else "dd MMM yyyy HH:mm"
        return SimpleDateFormat(pattern, spanishLocale).format(date)
            .replace(".", "")
            .replace("sept", "sep")
    }

    private fun isCurrentYear(date: Date): Boolean {
        val now = Calendar.getInstance()
        val other = Calendar.getInstance().apply { time = date }
        return now.get(Calendar.YEAR) == other.get(Calendar.YEAR)
    }
}
