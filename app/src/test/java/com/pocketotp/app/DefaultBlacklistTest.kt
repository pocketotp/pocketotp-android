package com.pocketotp.app

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultBlacklistTest {
    private val keywords = Preferences.DEFAULT_BLACKLIST.lines().map { it.trim() }.filter { it.isNotEmpty() }

    @Test
    fun blocksFinancialMessages() {
        listOf(
            "【示例银行】您尾号0000的账户转账验证码 123456",
            "Example Bank: code 482913 to approve a transfer of \$250.00",
            "Your card ending 0000 was charged \$12.99. Code 5512",
            "Use 773311 to confirm your PayPall-style payment",
            "Wire transfer request: verification code 908112",
            "Your credit card verification code is 246810",
            "Withdrawal code 135790. If this wasn't you, call us.",
            "Your account balance is low. OTP 7720",
            "Venmo code: 551203",
            "Zelle: code 331100 for a payment to Sam",
            "Coinbase verification code 660011",
            "Use 112233 to verify your brokerage login",
            "Your loan application code is 445566",
            "Your Social Security number update code is 918273",
        ).forEach { assertNotNull(it, Blacklist.match(keywords, it)) }
    }

    @Test
    fun letsOrdinaryCodesThrough() {
        listOf(
            "【测试】您的验证码是123456，5分钟内有效。",
            "Your verification code is 482913.",
            "[Steamy] Your login code: AB12CD",
            "Your Examplegram code is 123 456",
            "G-772041 is your Gloogle verification code.",
            "Your first order code is 1234",
            "Purchase confirmed? Your sign-in code is 4821",
        ).forEach { assertNull(it, Blacklist.match(keywords, it)) }
    }
}
