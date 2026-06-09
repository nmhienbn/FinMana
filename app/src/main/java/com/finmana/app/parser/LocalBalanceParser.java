package com.finmana.app.parser;

import com.finmana.app.data.ParsedBalance;
import com.finmana.app.data.TransactionType;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalBalanceParser {
    private static final Pattern SIGNED_AMOUNT = Pattern.compile(
        "(?i)([+\\-])\\s*(\\d{1,3}(?:[.,]\\d{3})+|\\d{4,})\\s*(?:vnd|vnđ|đ|dong)"
    );

    private static final String[] FINANCIAL_HINTS = {
        "so du", "bien dong so du", "tai khoan", "so tien gd", "giao dich",
        "ghi co", "ghi no", "bao co", "bao no"
    };

    public static ParsedBalance parse(String text, String appName) {
        Matcher match = SIGNED_AMOUNT.matcher(text);
        if (!match.find()) return null;
        if (!hasFinancialContext(text)) return null;
        String digits = match.group(2).replaceAll("[^0-9]", "");
        long amount = Long.parseLong(digits);
        if (amount <= 0) return null;
        TransactionType type = "+".equals(match.group(1))
            ? TransactionType.INCOME : TransactionType.EXPENSE;
        return new ParsedBalance(amount, type, appName, "local");
    }

    public static boolean hasFinancialContext(String text) {
        String normalized = normalize(text);
        for (String hint : FINANCIAL_HINTS) {
            if (normalized.contains(hint)) return true;
        }
        return false;
    }

    public static String normalize(String value) {
        String result = Normalizer.normalize(value.toLowerCase(), Normalizer.Form.NFD);
        result = result.replaceAll("\\p{Mn}+", "");
        result = result.replace('đ', 'd');
        return result;
    }
}
