package com.finmana.app.parser

import com.finmana.app.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalBalanceParserTest {
    @Test
    fun parsesMbExpenseFromSignedTransactionAmount() {
        val text = "Thông báo biến động số dư\nTK 03xxx689|GD: -20,000VND 09/06/26 09:01 |SD: 23,523,322VND"
        val result = LocalBalanceParser.parse(text, "MB Bank")

        assertEquals(20_000L, result?.amount)
        assertEquals(TransactionType.EXPENSE, result?.type)
    }

    @Test
    fun parsesBidvIncomeFromSignedTransactionAmount() {
        val text = "Thông báo BIDV\nSố tiền GD: +20,000 VND\nSố dư cuối: 20,072 VND"
        val result = LocalBalanceParser.parse(text, "SmartBanking")

        assertEquals(20_000L, result?.amount)
        assertEquals(TransactionType.INCOME, result?.type)
    }

    @Test
    fun ignoresPromotionWithDiscountAmounts() {
        val text = "MỜI VẠN DEAL HOT BẤT TẬN, GIẢM 20.000Đ\nMã giảm giá đã có trong ví"
        assertNull(LocalBalanceParser.parse(text, "ShopeeFood"))
    }

    @Test
    fun ignoresSecurityEmailContainingYear() {
        val text = "Cảnh báo bảo mật\nFinMana đã được cấp quyền truy cập\n© 2026 Google LLC"
        assertNull(LocalBalanceParser.parse(text, "Gmail"))
    }
}
