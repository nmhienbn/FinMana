package com.finmana.app.parser

import com.finmana.app.data.ParsedBalance
import com.finmana.app.data.TransactionType
import java.text.Normalizer

object LocalBalanceParser {
    private val signedAmount = Regex(
        """(?i)([+\-])\s*(\d{1,3}(?:[.,]\d{3})+|\d{4,})\s*(?:vnd|vnđ|đ|dong)"""
    )
    private val financialHints = listOf(
        "so du", "bien dong so du", "tai khoan", "so tien gd", "giao dich",
        "ghi co", "ghi no", "bao co", "bao no"
    )

    fun parse(text: String, appName: String): ParsedBalance? {
        val match = signedAmount.find(text) ?: return null
        if (!hasFinancialContext(text)) return null
        val amount = match.groupValues[2].filter(Char::isDigit).toLongOrNull()
            ?.takeIf { it > 0 } ?: return null
        val type = if (match.groupValues[1] == "+") TransactionType.INCOME else TransactionType.EXPENSE
        return ParsedBalance(amount, type, appName, "local")
    }

    fun hasFinancialContext(text: String): Boolean {
        val normalized = normalize(text)
        return financialHints.any(normalized::contains)
    }

    fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd')
}

