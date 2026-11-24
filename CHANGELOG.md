# Changelog

All notable changes to the PocketOTP Android app. Versions match `versionName`. Release tags are `v<version>`.
The release workflow attaches the section for the tagged version to the GitHub Release.

## [2.5.1] - 2026-11-24

First build signed with the PocketOTP release key (see [docs/verify-apk.md](docs/verify-apk.md)).
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
- QR pairing: `pocketotp://pair` deep link, in-app scanner, or a pasted link. A short-lived one-time code is exchanged for a per-device token after confirmation on the phone. See [docs/pairing.md](docs/pairing.md).

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
