package com.finmana.app.parser

import com.finmana.app.data.ParsePattern
import com.finmana.app.data.ParsedBalance
import com.finmana.app.data.PatternDao
import com.finmana.app.data.TransactionType

class BalanceParser(
    private val patternDao: PatternDao,
    private val aiParser: AiParser
) {
    suspend fun parse(packageName: String, appName: String, text: String): ParsedBalance? {
        LocalBalanceParser.parse(text, appName)?.let { return it }
        if (!LocalBalanceParser.hasFinancialContext(text)) return null

        val normalized = LocalBalanceParser.normalize(text)
        patternDao.forPackage(packageName).forEach { pattern ->
            parsePattern(pattern, text, normalized, appName)?.let { return it }
        }

        val aiResult = aiParser.parse(packageName, appName, text) ?: return null
        aiResult.learnedPattern?.let { pattern ->
            if (runCatching { Regex(pattern.regex).find(text) }.getOrNull() != null) {
                patternDao.insert(pattern.copy(sourcePackage = packageName, learnedByAi = true))
            }
        }
        return aiResult.balance
    }

    private fun parsePattern(
        pattern: ParsePattern,
        originalText: String,
        normalizedText: String,
        appName: String
    ): ParsedBalance? {
        val match = runCatching { Regex(pattern.regex, RegexOption.IGNORE_CASE).find(originalText) }.getOrNull()
            ?: return null
        val amount = match.groups[pattern.amountGroup]?.value?.let(::parseAmount) ?: return null
        val direction = pattern.directionGroup?.let { match.groups[it]?.value } ?: normalizedText
        val normalizedDirection = LocalBalanceParser.normalize(direction)
        val type = when {
            pattern.incomeWords.split(",").any {
                LocalBalanceParser.normalize(it).let(normalizedDirection::contains)
            } -> TransactionType.INCOME
            pattern.expenseWords.split(",").any {
                LocalBalanceParser.normalize(it).let(normalizedDirection::contains)
            } -> TransactionType.EXPENSE
            else -> return null
        }
        return ParsedBalance(amount, type, appName, if (pattern.learnedByAi) "learned" else "pattern")
    }

    private fun parseAmount(raw: String): Long? {
        val digits = raw.filter(Char::isDigit)
        return digits.toLongOrNull()?.takeIf { it > 0 }
    }
}

