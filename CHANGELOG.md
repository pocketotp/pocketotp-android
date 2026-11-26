# Changelog

All notable changes to the PocketOTP Android app. Versions match `versionName`. Release tags are `v<version>`.
The release workflow attaches the section for the tagged version to the GitHub Release.

## [2.5.4] - Unreleased

### Changed
- Code extraction: split codes such as `123-456`, `123 456` and `4821-7730` are recognised and returned without the separator. Letter+digit codes (`K7Q2M9`) are picked up when they directly follow a code keyword; promo/coupon messages are excluded. Full-width digits are normalised.
  支持带分隔的验证码（123-456、123 456），返回时去掉分隔符；紧跟在关键词后的字母数字码（如 K7Q2M9）也能识别，优惠码类短信不算。全角数字会先转成半角。
- Brand detection: besides `【品牌】`, also `[Brand]`, a leading `Brand:` and `Your Brand code is`.
  品牌识别除了【】，新增 [Brand]、开头的 "Brand:" 和 "Your Brand code is" 写法。
- Default blacklist adds English financial and sensitive keywords (transfer, payment, credit card, PayPal, Venmo, Zelle, Coinbase, loan, Social Security, …). Existing installs keep their saved list.
  默认黑名单加入英文金融和敏感词。已经保存过黑名单的用户不受影响。

### Tests
- Unit tests for the extractor with 35 Chinese and 34 English made-up samples, plus default-blacklist tests.

## [2.5.3] - 2026-11-26

### Fixed
- On a Free plan, codes from the first SIM were rejected as "not on the plan" and codes from the second SIM were uploaded instead. The app was sending a 0-based slot index. Uploads now send the 1-based slot (and 0-based simSlotIndex).
  免费档会把第一张卡的验证码误判为「不在套餐内」，第二张卡的反而能上传。原因是上传时把从 0 起算的卡槽序号当成了从 1 起算的 slot。现在会同时发送正确的 1-based slot 和 0-based simSlotIndex。

## [2.5.2] - 2026-11-25

### Fixed
- Permission rows on onboarding and in Settings: the "Recommended" badge no longer wraps one letter per line when the title is long or the screen is narrow. The badge stays on a single line and moves under the title when it does not fit.
  权限清单里的「建议」标签在标题较长或窄屏时，不再竖排成单个字母。

### Changed
- Launcher icon is now the OTP-dots P mark, with a monochrome layer for Android 13+ themed icons.
  应用图标换成带验证码圆点的 P 标志，并加入 Android 13+ 主题图标的单色图层。

## [2.5.1] - 2026-11-24

First build signed with the PocketOTP release key (see [docs/verify-apk.md](https://github.com/pocketotp/pocketotp-android/blob/main/docs/verify-apk.md)).
If you installed an earlier debug build, uninstall it first. Android won't update across different signing keys.
Then install this APK and pair again by scanning the QR code.

### Added
- Hosted-relay account states. 401 `device_revoked` (HTTP or WebSocket close 4001) clears the token, shows a notification and leads to re-pairing.
- Home shows the plan, SIMs that aren't on the plan, suspended devices (`device_suspended`), the used-up monthly quota (`quota_exceeded`) and `number_not_on_plan`.
- Clear pairing errors for a wrong or invalid code, an expired code, and a full account (`device_limit`: "设备已满，请解绑或升级 Pro").

### Changed
- On WebSocket, presence is posted every 10 minutes so plan changes are picked up.
- Fields the old single-user relay doesn't send are ignored.

## [2.5.0] - 2026-11-24

### Added
- First-run setup wizard with a live permission checklist and one-tap fixes: SMS, phone state, notifications, full-screen intent, battery optimisation, background pop-up, and Samsung "never sleeping apps" guidance.
- QR pairing: `pocketotp://pair` deep link, in-app scanner, or a pasted link. A short-lived one-time code is exchanged for a per-device token after confirmation on the phone. See [docs/pairing.md](https://github.com/pocketotp/pocketotp-android/blob/main/docs/pairing.md).

## [2.4.0] - 2026-11-23

### Changed
- Redesigned Home, History and Settings in Compose and Material 3.
- Codes are masked by default; long-press reveals one for 10 seconds.
- Bank and payment messages are shown as blocked, without their codes.

## [2.3.0] - 2026-11-23

### Changed
- Redesigned the send-confirmation screen (Compose, Material 3, long-press to send).
- A pending confirmation is shown again when the app is opened after Android blocked a background launch.

## [2.2.x] - 2026-11

### Changed
- Renamed the app to PocketOTP (`com.pocketotp.app`).
- The confirm screen opens from the background and its buttons stay above the navigation bar.

## [2.1.2]

### Added
- Confirm-before-send for outgoing SMS. Delivery reporting fixed.
