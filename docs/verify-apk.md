# Verifying a PocketOTP APK

Official PocketOTP APKs come from [GitHub Releases](https://github.com/pocketotp/pocketotp-android/releases) or [pocketotp.com](https://pocketotp.com). They are signed with one release key.

**Release certificate**

| | |
|---|---|
| Subject | `CN=PocketOTP, O=VOX VERBI` |
| Key | RSA 4096 |
| Valid | 2026-11-24 → 2056-11-23 |
| **SHA-256** | `7F:CA:5C:4E:36:08:17:1F:85:91:3E:C4:C8:A0:18:AF:94:C5:D9:64:2A:88:8D:F6:FF:CC:28:0E:88:C3:4D:DF` |
| SHA-1 | `8B:08:74:77:05:EA:12:EE:99:3C:FC:87:9D:89:F4:57:C6:A7:26:5F` |

If the fingerprint doesn't match, don't install the APK.

## 1. Check the file hash

Each release lists `pocketotp-<version>.apk.sha256`.

```sh
sha256sum -c pocketotp-2.5.1.apk.sha256      # Linux
shasum -a 256 pocketotp-2.5.1.apk            # macOS, compare by eye
Get-FileHash pocketotp-2.5.1.apk             # Windows PowerShell
```

## 2. Check the signing certificate

With the Android SDK build-tools:

```sh
apksigner verify --print-certs pocketotp-2.5.1.apk
# Signer #1 certificate SHA-256 digest: 7fca5c4e3608171f85913ec4c8a018af94c5d9642a888df6ffcc280e88c34ddf
```

Releases use APK Signature Scheme v2/v3 (minSdk 26, no v1 JAR signature), so `keytool -printcert -jarfile` won't show the certificate. Use `apksigner`, which is in Android SDK build-tools 30+.

On the phone, Android refuses to update an installed PocketOTP with an APK signed by a different key. That's a second safety net.

## Debug builds

Builds before 2.5.1, and anything you build yourself with `assembleDebug`, use a debug key. To move to an official release you have to uninstall once. After that, pair again by scanning the QR code.
