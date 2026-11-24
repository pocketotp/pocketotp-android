# PocketOTP pairing contract (v1)

This describes how the hosted multi-user relay pairs a phone with an account using a QR code. The Android client implements it starting in **2.5.0** (`app/src/main/java/com/pocketotp/app/Pairing.kt`, tests in `PairingTest.kt`). The relay side is not built yet. This document is the contract it should implement.

## Goals

- **No long-lived secret in the QR code.** The QR holds a short-lived, single-use pairing code. The phone exchanges it for its own device token.
- **One token per device.** Each paired phone can be listed, rate-limited and revoked on its own.
- **Manual entry stays.** Self-hosted / legacy relays keep working with relay URL + bearer token. The debug `com.pocketotp.app.CONFIGURE` intent also stays.

## 1. Pairing link (QR payload)

```
pocketotp://pair?v=1&relay=<url-encoded https base URL>&code=<pairing code>[&exp=<unix seconds>]
```

Example:

```
pocketotp://pair?v=1&relay=https%3A%2F%2Frelay.pocketotp.com&code=7K3M-Q9TD-2XHF&exp=1764000600
```

| Param | Required | Rules |
|---|---|---|
| `v` | no (defaults to `1`) | Integer. The app rejects any other version and asks the user to update. Bump it only for incompatible changes. |
| `relay` | yes | Absolute **https** URL with a host and no userinfo, query or fragment. A path is allowed (e.g. `https://relay.pocketotp.com/v1`). The app strips a trailing `/`. |
| `code` | yes | Pairing code, see §2. Dashes and spaces are ignored. |
| `exp` | no | Unix seconds. Informational: the app rejects a link already past `exp` without calling the relay. The relay must still enforce expiry itself. |

- Unknown parameters are ignored. If a parameter repeats, the first one wins.
- Scheme and host are matched case-insensitively.
- The app registers `pocketotp://pair` as a BROWSABLE deep link. Scanning the QR with the system camera opens PocketOTP directly. The in-app scanner (ZXing, no Google Play services needed) and a "paste link" field accept the same string.
- **The app never claims automatically.** It always shows a confirmation dialog with the relay host and the code. It warns when an existing configuration would be replaced.
- Keep the QR at error-correction level M or higher. The whole link should stay under ~200 characters.

## 2. Pairing code

- Alphabet: Crockford base32 without `I L O U`: `0123456789ABCDEFGHJKMNPQRSTVWXYZ`.
- Length: the client accepts 8–16 characters. The relay **should issue 12** (60 bits), shown grouped by 4 (`7K3M-Q9TD-2XHF`) for manual typing.
- The client upper-cases, strips `-` and spaces, and maps look-alikes `O→0`, `I→1`, `L→1` before sending. The relay should normalise the same way.
- **Single use**, **TTL 10 minutes** (recommended).
- Store only a hash (e.g. SHA-256 of the normalised code). Bind it to the account that generated it.
- Rate-limit claims, for example 10 per IP per 10 minutes plus a global failure budget per code prefix, and lock a code after 5 failed attempts against it.

## 3. Claim: `POST {relay}/pair/claim`

No `Authorization` header. JSON body sent by the app:

```json
{
  "v": 1,
  "code": "7K3MQ9TD2XHF",
  "device": {
    "name": "SM-F7410",
    "model": "samsung SM-F7410",
    "platform": "android",
    "appVersion": "2.5.0",
    "caps": ["confirm"]
  }
}
```

### Success — `200`

```json
{
  "token": "<device bearer token>",
  "deviceId": "dev_01J…",
  "relay": "https://relay.pocketotp.com",
  "account": { "label": "Jimmy" }
}
```

- `token` (required): long-lived bearer token for this device only. The app uses it exactly like today's token: `Authorization: Bearer <token>` on `/sms`, `/phone/*`, and the WebSocket. Store only a hash server-side.
- `deviceId` (optional): stored by the app, used for display and future revocation UX.
- `relay` (optional): canonical base URL. **The app accepts it only if its host equals the host in the QR**, otherwise it keeps the QR's `relay`. This prevents a reply from redirecting the device to another server.
- `account.label` (optional): shown to the user.
- After a successful claim the code is consumed. A second claim with the same code must fail.

### Errors

Return JSON `{"error": "<code>"}` with these statuses. The app maps them to Chinese messages; it also shows unknown codes, just less specifically.

| HTTP | `error` | Meaning |
|---|---|---|
| 400 | `bad_request` | Malformed body / unsupported `v`. |
| 404 | `code_invalid` | No such code (do not distinguish from used). |
| 410 | `code_expired` | Code past TTL, or already used. |
| 429 | `rate_limited` | Too many attempts. Optionally send `Retry-After`. |
| 5xx | any | The app shows a generic failure and lets the user retry. |

## 4. After pairing

- The app saves relay + token (+ deviceId), turns forwarding on, and starts the foreground service. It then connects as usual and advertises `caps:["confirm"]`.
- **Revocation:** when the relay revokes a device, it should reject that token with `401 {"error":"device_revoked"}` (HTTP and WebSocket). The app will surface re-pairing in a later version. Today it shows as offline / upload failure.
- **Re-pairing:** claiming a new code on a phone that is already paired replaces its stored token. The relay may revoke the old device token for the same `deviceId` or model, or leave that to the account owner.

## 5. Security notes

- The only secret that travels through the QR is a one-time code. It is short-lived and becomes useless after the first claim.
- The relay URL must be https. The app refuses http and credentials in the URL.
- Every pairing requires explicit user confirmation on the phone, showing the relay host. That covers QR codes from untrusted sources and malicious deep links.
- Do not log full codes or tokens on either side.

## 6. Test vectors

| Input | Result |
|---|---|
| `pocketotp://pair?v=1&relay=https%3A%2F%2Frelay.pocketotp.com&code=7K3M-Q9TD-2XHF` | OK, relay `https://relay.pocketotp.com`, code `7K3MQ9TD2XHF` |
| `POCKETOTP://pair?relay=https://r.example.com/&code=7k3mq9td2xhf&foo=bar` | OK, relay `https://r.example.com` |
| `pocketotp://pair?v=2&…` | `UNSUPPORTED_VERSION` |
| `relay=http://…` or `https://user:pw@…` | `BAD_RELAY` |
| `code=SHORT` / non-alphabet characters | `BAD_CODE` |
| `exp` in the past | `EXPIRED` (no network call) |
