package com.finmana.app.data;

public class ChartPoint {
    public final String label;
    public final long income;
    public final long expense;

    public ChartPoint(String label, long income, long expense) {
        this.label = label;
        this.income = income;
        this.expense = expense;
    }
}
