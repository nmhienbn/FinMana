package com.finmana.app.parser;

import com.finmana.app.data.ParsePattern;
import com.finmana.app.data.ParsedBalance;
import com.finmana.app.data.PatternDao;
import com.finmana.app.data.TransactionType;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BalanceParser {
    private final PatternDao patternDao;
    private final AiParser aiParser;

    public BalanceParser(PatternDao patternDao, AiParser aiParser) {
        this.patternDao = patternDao;
        this.aiParser = aiParser;
    }

    public ParsedBalance parse(String packageName, String appName, String text) {
        ParsedBalance local = LocalBalanceParser.parse(text, appName);
        if (local != null) return local;

        if (!LocalBalanceParser.hasFinancialContext(text)) return null;

        String normalized = LocalBalanceParser.normalize(text);
        List<ParsePattern> patterns = patternDao.forPackage(packageName);
        for (ParsePattern pattern : patterns) {
            ParsedBalance result = parsePattern(pattern, text, normalized, appName);
            if (result != null) return result;
        }

        AiParseResult aiResult = aiParser.parse(packageName, appName, text);
        if (aiResult == null) return null;

        if (aiResult.learnedRegex != null) {
            try {
                Pattern regex = Pattern.compile(aiResult.learnedRegex);
                if (regex.matcher(text).find()) {
                    ParsePattern learned = new ParsePattern();
                    learned.sourcePackage = packageName;
                    learned.regex = aiResult.learnedRegex;
                    learned.amountGroup = 1;
                    learned.learnedByAi = true;
                    patternDao.insert(learned);
                }
            } catch (Exception ignored) {}
        }
        return aiResult.balance;
    }

    private ParsedBalance parsePattern(ParsePattern pattern, String originalText,
                                        String normalizedText, String appName) {
        Pattern regex;
        try {
            regex = Pattern.compile(pattern.regex, Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            return null;
        }
        Matcher match = regex.matcher(originalText);
        if (!match.find()) return null;

        String amountStr = match.group(pattern.amountGroup);
        if (amountStr == null) return null;
        Long amount = parseAmount(amountStr);
        if (amount == null) return null;

        String direction = pattern.directionGroup != null
            ? match.group(pattern.directionGroup) : normalizedText;
        String normalizedDirection = LocalBalanceParser.normalize(
            direction != null ? direction : "");

        boolean isIncome = false, isExpense = false;
        for (String word : pattern.incomeWords.split(",")) {
            if (normalizedDirection.contains(LocalBalanceParser.normalize(word))) {
                isIncome = true;
                break;
            }
        }
        if (!isIncome) {
            for (String word : pattern.expenseWords.split(",")) {
                if (normalizedDirection.contains(LocalBalanceParser.normalize(word))) {
                    isExpense = true;
                    break;
                }
            }
        }
        TransactionType type;
        if (isIncome) type = TransactionType.INCOME;
        else if (isExpense) type = TransactionType.EXPENSE;
        else return null;

        return new ParsedBalance(amount, type, appName,
            pattern.learnedByAi ? "learned" : "pattern");
    }

    private Long parseAmount(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        long value = Long.parseLong(digits);
        return value > 0 ? value : null;
    }
}
