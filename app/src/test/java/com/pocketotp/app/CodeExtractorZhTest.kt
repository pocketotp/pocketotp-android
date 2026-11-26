package com.pocketotp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chinese SMS samples. All codes, brands and numbers are made up.
 * These pin the behaviour from 2.5.3 and earlier: none may regress.
 */
class CodeExtractorZhTest {
    private data class Case(val body: String, val code: String?, val brand: String?)

    private val cases = listOf(
        Case("【测试】您的验证码是123456，5分钟内有效。", "123456", "测试"),
        Case("【示例银行】验证码：482913，请勿泄露。", "482913", "示例银行"),
        Case("【某某出行】验证码 7351，10分钟内有效，请勿告诉他人。", "7351", "某某出行"),
        Case("【小蓝书】你的登录验证码为 905172，如非本人操作请忽略。", "905172", "小蓝书"),
        Case("您的校验码为 66120384，请在页面中输入。", "66120384", null),
        Case("【云端网盘】动态码：31415，有效期3分钟。", "31415", "云端网盘"),
        Case("【某外卖】确认码 208864，配送员到达时出示。", "208864", "某外卖"),
        Case("【繁體商城】您的動態碼為 551203，請於5分鐘內輸入。", "551203", "繁體商城"),
        Case("【台灣電信】確認碼：7720，請勿提供給他人。", "7720", "台灣電信"),
        Case("【某證券】檢驗碼 903311，切勿外洩。", "903311", "某證券"),
        Case("839201是您的验证码，请于5分钟内填写。【快递助手】", "839201", "快递助手"),
        Case("【阅读App】 1234 为您的验证码，有效期 2 分钟。", "1234", "阅读App"),
        Case("验证码：520131。您正在登录示例账号，若非本人操作请修改密码。", "520131", null),
        Case("【示例云】您正在进行身份验证，验证码 774411，30分钟内有效。", "774411", "示例云"),
        Case("【示例视频】尊敬的用户，您的验证码为：009876。", "009876", "示例视频"),
        Case("【示例商城】验证码 66 不要告诉别人", null, "示例商城"),
        Case("【示例社区】欢迎回来！本月共有 12 条新消息。", null, "示例社区"),
        Case("您的快递已到达驿站，取件码 3-2-1508，请及时领取。", null, null),
        Case("【示例游戏】账号安全验证码：3388，验证码5分钟内有效。", "3388", "示例游戏"),
        Case("【示例旅行】您预订的订单 20261126 已确认，验证码 482100。", "482100", "示例旅行"),
        Case("【示例健身】您的验证码是 135790 (5分钟有效)", "135790", "示例健身"),
        Case("【 空格品牌 】验证码 246810", "246810", "空格品牌"),
        Case("【示例音乐】验证码:8642,请勿转发。", "8642", "示例音乐"),
        Case("【示例地图】您的验证码为24680135，请在10分钟内完成验证。", "24680135", "示例地图"),
        Case("尊敬的客户，您本次操作的校验码是 1357，切勿告知他人。", "1357", null),
        Case("【示例办公】您正在绑定手机，动态码 902211，如非本人操作请忽略本短信。", "902211", "示例办公"),
        Case("【示例医院】预约成功，就诊时间 2026年12月03日 09:30，请准时到达。", null, "示例医院"),
        Case("【示例政务】您的验证码 557799，用于实名信息核验。", "557799", "示例政务"),
        Case("【示例学习】验证码 4455，有效期 120 秒。", "4455", "示例学习"),
        Case("【示例直播】验证码123456，请在App内输入。退订回T", "123456", "示例直播"),
        Case("【示例邮箱】您的安全验证码为 618203。", "618203", "示例邮箱"),
        Case("【示例打车】司机已接单，车牌 京A12345，预计 3 分钟到达。", null, "示例打车"),
        Case("【示例论坛】您正在修改密码，校验码：700123。", "700123", "示例论坛"),
        Case("【示例外卖】您的订单已送达，祝您用餐愉快。", null, "示例外卖"),
        Case("【示例会员】验证码 9081 ，请勿泄露给任何人，包括客服。", "9081", "示例会员"),
    )

    @Test
    fun atLeastThirtySamples() {
        assertTrue(cases.size >= 30)
    }

    @Test
    fun extractsCodeAndBrand() {
        val failures = cases.mapNotNull { c ->
            val got = CodeExtractor.extract(c.body)
            if (got.code == c.code && got.brand == c.brand) null
            else "${c.body}\n  expected code=${c.code} brand=${c.brand}\n  got      code=${got.code} brand=${got.brand}"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun verificationDetectionForZh() {
        val body = "【示例社区】欢迎回来！本月共有 12 条新消息。"
        assertEquals(false, CodeExtractor.looksLikeVerification(body, CodeExtractor.extract(body)))
        val otp = "【测试】您的验证码是123456，5分钟内有效。"
        assertEquals(true, CodeExtractor.looksLikeVerification(otp, CodeExtractor.extract(otp)))
    }
}
