package com.finmana.app.parser;

import com.finmana.app.data.ParsedBalance;
import com.finmana.app.data.TransactionType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LocalBalanceParserTest {
    @Test
    public void parsesMbExpenseFromSignedTransactionAmount() {
        String text = "Thông báo biến động số dư\nTK 03xxx689|GD: -20,000VND 09/06/26 09:01 |SD: 23,523,322VND";
        ParsedBalance result = LocalBalanceParser.parse(text, "MB Bank");

        assertEquals(20_000L, result.amount);
        assertEquals(TransactionType.EXPENSE, result.type);
    }

    @Test
    public void parsesBidvIncomeFromSignedTransactionAmount() {
        String text = "Thông báo BIDV\nSố tiền GD: +20,000 VND\nSố dư cuối: 20,072 VND";
        ParsedBalance result = LocalBalanceParser.parse(text, "SmartBanking");

        assertEquals(20_000L, result.amount);
        assertEquals(TransactionType.INCOME, result.type);
    }

    @Test
    public void ignoresPromotionWithDiscountAmounts() {
        String text = "MỜI VẠN DEAL HOT BẤT TẬN, GIẢM 20.000Đ\nMã giảm giá đã có trong ví";
        assertNull(LocalBalanceParser.parse(text, "ShopeeFood"));
    }

    @Test
    public void ignoresSecurityEmailContainingYear() {
        String text = "Cảnh báo bảo mật\nFinMana đã được cấp quyền truy cập\n© 2026 Google LLC";
        assertNull(LocalBalanceParser.parse(text, "Gmail"));
    }
}
