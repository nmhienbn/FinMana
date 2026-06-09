package com.finmana.app.data;

public class ParsedBalance {
    public final long amount;
    public final TransactionType type;
    public final String sourceName;
    public final String parser;

    public ParsedBalance(long amount, TransactionType type, String sourceName, String parser) {
        this.amount = amount;
        this.type = type;
        this.sourceName = sourceName;
        this.parser = parser;
    }
}
