# Security policy

## Reporting a vulnerability

Please don't open a public issue. Email **security@pocketotp.com** with steps to reproduce, the affected version, and your device or Android version. We aim to reply within 3 business days and to ship a fix or mitigation within 30 days, and we'll credit you if you want.

## Scope

- This Android app: SMS handling, pairing, the confirm-before-send flow, local storage and the relay protocol client.
- The hosted relay at `*.pocketotp.com` is in scope for reports. Testing must use your own account, your own phone and your own numbers only. No load testing, no access to other users' data, no social engineering.

## Design commitments

- Only verification codes are forwarded. Inbox history is never read in bulk. Bank and payment messages are blocked by default.
- Every outgoing SMS requires explicit confirmation on the phone.
- Pairing uses short-lived one-time codes. Device tokens never appear in QR codes or logs.
- Release APKs are signed with a single PocketOTP key. Check the certificate before installing: [docs/verify-apk.md](docs/verify-apk.md).

## Supported versions

Only the latest release gets security fixes.
