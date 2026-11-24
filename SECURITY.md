# Security policy

## Reporting a vulnerability

Please don't open a public issue. Email **hello@pocketotp.com** with "Security" in the subject. Include steps to reproduce, the affected version, and your device or Android version.

- We acknowledge reports within **72 hours**.
- We send an initial assessment within **7 days**.
- We aim to fix critical issues within **30 days**, and to coordinate disclosure with you.
- We'll credit you in the release notes if you want.

## Scope

- This Android app: SMS handling, pairing, the confirm-before-send flow, local storage and the relay protocol client.
- The hosted service (`pocketotp.com`, `relay.pocketotp.com`) is in scope for reports. Testing must use your own account, your own phone and your own numbers only. No load testing, no access to other users' data, no social engineering.

## Design commitments

- Only verification codes are forwarded. Inbox history is never read in bulk. Bank and payment messages are blocked by default.
- Every outgoing SMS requires explicit confirmation on the phone.
- Pairing uses short-lived one-time codes. Device tokens never appear in QR codes or logs.
- Release APKs are signed with a single PocketOTP key. Check the certificate before installing: [docs/verify-apk.md](docs/verify-apk.md).

## Supported versions

Only the latest release gets security fixes.
