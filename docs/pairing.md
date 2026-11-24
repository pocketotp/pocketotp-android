# PocketOTP pairing contract (v1) — canonical

This file is kept **identical** in `pocketotp/pocketotp-relay` and `pocketotp/pocketotp-android`.
Change it in both repos in the same PR pair.

- Relay: implemented in `pocketotp-relay` (`src/auth.js` `createPairing` / `claimPairing`, tests in `test/pairing.test.js`).
  Dev relay: `https://pocketotp-relay-dev.jimmymjing.workers.dev`.
- Android: implemented from **2.5.0** (`app/src/main/java/com/pocketotp/app/Pairing.kt`, tests in `PairingTest.kt`).

## Goals

- **No long-lived secret in the QR code.** The QR holds a short-lived, single-use pairing code. The phone exchanges it for its own device token.
- **One token per device.** Each paired phone can be listed, rate-limited and revoked on its own.
- **Manual entry stays.** Self-hosted / legacy relays keep working with relay URL + bearer token. The debug `com.pocketotp.app.CONFIGURE` intent also stays.

## 1. Getting a code (account owner, web console)

`POST {relay}/v1/pairing` with `Authorization: Bearer <session token>` (the web console does this):

```json
{
  "ok": true,
  "code": "7K3M-Q9TD-2XHF",
  "expiresAt": "2026-11-24T08:40:00.000Z",
  "exp": 1795509600,
  "relay": "https://relay.pocketotp.com",
  "qr": "pocketotp://pair?v=1&relay=https%3A%2F%2Frelay.pocketotp.com&code=7K3M-Q9TD-2XHF&exp=1795509600"
}
```

- Creating a new code invalidates the previous unused code of the same account.
- `402 device_limit` if the account is already at its device limit (Free 1, Pro 3). Max 20 codes / hour / account.

## 2. Pairing link (QR payload)

```
pocketotp://pair?v=1&relay=<url-encoded https base URL>&code=<pairing code>[&exp=<unix seconds>]
```

| Param | Required | Rules |
|---|---|---|
| `v` | no (defaults to `1`) | Integer. The app rejects any other version and asks the user to update. The relay always emits `v=1`. Bump only for incompatible changes. |
| `relay` | yes | Absolute **https** URL with a host and no userinfo, query or fragment. A path is allowed. The app strips a trailing `/`. The hosted relay serves everything at the root, so it emits no path. |
| `code` | yes | Pairing code, see §3. Dashes and spaces are ignored. The relay emits the grouped form `XXXX-XXXX-XXXX`. |
| `exp` | no | Unix seconds. The relay always emits it. Informational: the app rejects a link already past `exp` without calling the relay. The relay enforces expiry itself. |

- Unknown parameters are ignored. If a parameter repeats, the first one wins.
- Scheme and host are matched case-insensitively.
- The app registers `pocketotp://pair` as a BROWSABLE deep link; the in-app scanner (ZXing) and a "paste link" field accept the same string.
- **The app never claims automatically.** It always shows a confirmation dialog with the relay host and the code, and warns when an existing configuration would be replaced.
- QR error-correction level M or higher; link stays under ~200 characters (the hosted relay's link is ~110).

## 3. Pairing code

- Alphabet: Crockford base32 without `I L O U`: `0123456789ABCDEFGHJKMNPQRSTVWXYZ`.
- The relay issues **12 characters** (60 bits), displayed as `XXXX-XXXX-XXXX`. Both sides accept 8–16 characters.
- Normalisation (both sides): upper-case, strip `-` and whitespace, map look-alikes `O→0`, `I→1`, `L→1`. Anything else outside the alphabet is rejected.
- **Single use**, **TTL 10 minutes**.
- The relay stores only SHA-256 of the normalised code, bound to the account that created it.
- Claims are rate-limited to **10 per IP per 10 minutes** (`429` + `Retry-After: 600`), plus a burst limit shared with sign-in of 20 requests per IP per minute (`429` + `Retry-After: 60`). With 60-bit codes, a 10-minute TTL and one live code per account, brute force is not practical, so there is no per-code lockout (a wrong code doesn't identify which code it was aimed at).

## 4. Claim: `POST {relay}/pair/claim`

No `Authorization` header. `POST /v1/pair/exchange` is a deprecated alias with identical behaviour.

Request (as sent by the app):

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

`v` is optional (default 1). `device` is optional; the relay uses `name` (falls back to `model`) as the display
name, records `appVersion`, and seeds `caps` so confirm-before-send is ready before the first heartbeat.
The legacy flat form `{ "code", "deviceName", "appVersion" }` is also accepted.

### Success — `200`

```json
{
  "ok": true,
  "token": "pk_dev_…",
  "deviceId": "dev_…",
  "relay": "https://relay.pocketotp.com",
  "account": { "label": "ji***@gmail.com", "plan": "free" },
  "limits": { "devices": 1, "numbers": 1, "codesPerMonth": 50, "sendsPerDay": 10 },
  "deviceToken": "pk_dev_…"
}
```

- `token` (required): long-lived bearer token for this device only, used as `Authorization: Bearer <token>` on `/sms`, `/phone/*` and the WebSocket. The relay stores only its hash. (`deviceToken` repeats it for older clients.)
- `deviceId`: stored by the app for display and revocation UX.
- `relay`: canonical base URL. **The app accepts it only if its host equals the host in the QR**, otherwise it keeps the QR's `relay`.
- `account.label`: masked account email, shown to the user. `account.plan` and `limits` are informational.
- The code is consumed; a second claim with the same code fails with `410 code_expired`.

### Errors

JSON `{"ok": false, "error": "<code>"}`:

| HTTP | `error` | Meaning |
|---|---|---|
| 400 | `bad_request` | Malformed JSON / body, unsupported `v`, code not 8–16 alphabet characters. |
| 402 | `device_limit` | The account already has its maximum number of phones (Free 1, Pro 3). |
| 404 | `code_invalid` | No such code. |
| 410 | `code_expired` | Code past its TTL, or already used. |
| 429 | `rate_limited` | Too many attempts; `Retry-After` in seconds. |
| 5xx | any | Generic failure; the user may retry. |

## 5. After pairing

The phone protocol is unchanged from the single-user relay (sms-relay 2.1.x), authenticated with the device token:

| Method & path | Notes |
|---|---|
| `POST /phone/presence` | heartbeat + SIMs + caps. Response adds `suspended`, `plan`, `allowedSlots` (e.g. `[1]`). |
| `GET /phone/ws` | WebSocket, unchanged. |
| `GET /phone/poll?timeout=20` | long-poll fallback, unchanged. |
| `POST /phone/confirm` | `{id, decision}`; `429 rate_limited_day` follows the plan's daily limit. |
| `POST /phone/result` | unchanged. |
| `POST /sms` (alias `/phone/sms`) | must include `slot` (1-based) or `simSlotIndex`. New errors: `402 quota_exceeded`, `403 number_not_on_plan`. |
| `GET /phone/me` | `{deviceId, deviceName, suspended, plan, limits, allowedSlots, usage}` |
| `POST /phone/unpair` | phone-initiated unpair; the token stops working. |

- Command ids look like `dev_xxx.<uuid>`; treat them as opaque.
- **Revocation:** a revoked device token gets `401 {"error":"device_revoked"}` on every HTTP route and on the WebSocket upgrade; open sockets are closed with code `4001` reason `device_revoked`. An unknown token gets `401 unauthorized`.
- **Plan states:** `allowedSlots` lists SIM slots on the plan (Free 1 number, Pro 2); codes from other slots get `403`.
  `suspended: true` / `402 device_suspended` means the account downgraded and this phone is beyond the device limit
  (presence keeps working). `402 quota_exceeded` means the Free monthly code quota (50, UTC month) is used up.
- **Re-pairing:** claiming a new code on an already-paired phone replaces its stored token. The relay does not
  revoke the old device automatically; the owner unpairs it in the console (it frees the device slot).

## 6. Security notes

- The only secret in the QR is a one-time code: short-lived and useless after the first claim.
- The relay URL must be https. The app refuses http and credentials in the URL.
- Every pairing requires explicit confirmation on the phone, showing the relay host.
- Neither side logs full codes or tokens. The relay stores hashes only.
- Compliance: only the user's own phone and SIMs; no number pool; codes live 15 minutes; history keeps metadata only.

## 7. Test vectors

| Input | Result |
|---|---|
| `pocketotp://pair?v=1&relay=https%3A%2F%2Frelay.pocketotp.com&code=7K3M-Q9TD-2XHF` | OK, relay `https://relay.pocketotp.com`, code `7K3MQ9TD2XHF` |
| `POCKETOTP://pair?relay=https://r.example.com/&code=7k3mq9td2xhf&foo=bar` | OK, relay `https://r.example.com` |
| `pocketotp://pair?v=2&…` | app: `UNSUPPORTED_VERSION`; relay claim with `v:2`: `400 bad_request` |
| `relay=http://…` or `https://user:pw@…` | app: `BAD_RELAY` |
| `code=SHORT` / non-alphabet characters | app: `BAD_CODE`; relay: `400 bad_request` |
| `exp` in the past | app: `EXPIRED` (no network call) |
| claim `7k3m q9td 2xhf`, `7K3M-Q9TD-2XHF`, `7K3MQ9TD2XHF` | same code on the relay |
| claim with `O`/`I`/`L` in place of `0`/`1`/`1` | same code on the relay |
| second claim of a used code | `410 code_expired` |

## 8. App-side follow-ups — done in Android 2.5.1

All four items below were implemented in app **2.5.1** (versionCode 12, commit `eb77af0`, merged via PR #4):

1. ✅ `404 code_invalid` and `400 bad_request` map to the specific "code wrong/invalid" message; `410` to expired/used.
2. ✅ `402 device_limit` shows "设备已满，请解绑或升级 Pro" (also when the body is missing).
3. ✅ `401 device_revoked` (any HTTP route, the WebSocket upgrade, or WS close `4001`) clears the token and routes
   to re-pairing. A plain `401` never clears the token.
4. ✅ `allowedSlots` / `suspended` / `plan` from presence are applied (SIMs show "not on plan"); `/sms`
   `402 quota_exceeded`, `403 number_not_on_plan` and `402 device_suspended` show Home banners. The app sends
   `POST /phone/presence` every 10 minutes while on WebSocket so plan state stays fresh.

Against the old single-user relay these fields are absent and nothing changes.
