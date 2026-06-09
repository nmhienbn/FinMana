package com.finmana.app.parser;

import com.finmana.app.data.ParsedBalance;

public class AiParseResult {
    public final ParsedBalance balance;
    public final String learnedRegex;

    public AiParseResult(ParsedBalance balance, String learnedRegex) {
        this.balance = balance;
        this.learnedRegex = learnedRegex;
    }
}
