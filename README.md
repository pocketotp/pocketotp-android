# PocketOTP for Android

Pre-release Android app that forwards SMS verification codes from **your own phone** to a hosted PocketOTP relay, so an AI agent can read them and, when asked, send a text from a chosen SIM.

This repository is the Android client only. The relay server is a **hosted, closed-source** service. You paste the service URL and an access token into the app; neither is stored in this repo.

**Status:** pre-release. The application id is `com.pocketotp.app` and the in-app name is **PocketOTP**. This id is different from the earlier `com.jimmy.smsrelay` builds, so installing 2.2.0 does not replace an existing SMS Relay install; grant permissions and enter the relay URL and token again, then disable or uninstall the old app so only one client receives SMS.

Requires **Android 8.0** (API 26) or later. Dual-SIM phones are supported.

## What it does

- Listens for incoming SMS on every SIM slot and records slot, subscription, and carrier.
- Extracts 4–8 digit codes (near 验证码 / 校验码 / 动态码 / code) and a `【brand】` tag when present.
- Uploads matching messages to the relay over HTTPS with your bearer token. Failed uploads are retried.
- Keeps a foreground service so the phone stays connected to the relay (WebSocket, with long-poll fallback).
- Sends SMS from a chosen SIM when the relay asks it to.
- Blocks bank and payment-related messages by default. The keyword list is editable in the app.
- Does **not** read the historical inbox. It only sees SMS that arrive while it is allowed to receive them.

## Permissions

| Permission | Why |
|---|---|
| `RECEIVE_SMS`, `READ_SMS` | Receive new texts. `READ_SMS` is required on some devices; the app does not scan the inbox. |
| `SEND_SMS` | Send a text when the relay requests it. |
| `READ_PHONE_STATE`, `READ_PHONE_NUMBERS` | Identify which SIM received a message or should send one. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Talk to the hosted relay. |
| `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `USE_FULL_SCREEN_INTENT` | Persistent notification, connection, and send-confirmation alerts. |
| `RECEIVE_BOOT_COMPLETED` | Restart after reboot if forwarding is enabled. |
| `WAKE_LOCK` | Keep the connection from sleeping. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | One-tap battery-optimization exemption from the setup checklist. |
| `SYSTEM_ALERT_WINDOW` | Optional. Granted manually; lets the send-confirmation screen open from the background on One UI. |
| `CAMERA` | Optional. Only used by the QR pairing scanner. |

## Pairing

From 2.5.0 the app can pair by scanning a QR code (`pocketotp://pair?v=1&relay=…&code=…`). The code is short-lived and is exchanged for a per-device token at `POST {relay}/pair/claim`. See [docs/pairing.md](docs/pairing.md). Manual entry of relay URL + token is still supported.

Google Play does not grant SMS permissions to ordinary apps, so this client is distributed as a sideloaded APK, not through Play.

## Build

You need JDK 17 and an Android SDK. Copy `local.properties.example` to `local.properties` and set `sdk.dir`.

```bash
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb`, then open the app, grant permissions, and enter your relay URL and access token.

Do not commit tokens, `local.properties`, keystores, or APKs.

## License

The Android app is licensed under the [Apache License 2.0](LICENSE). The hosted PocketOTP relay is not open source.
