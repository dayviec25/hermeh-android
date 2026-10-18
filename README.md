# Hermeh for Android

A native Android client for a self-hosted [Hermes agent](https://github.com/NousResearch/hermes-agent) dashboard. It began as a port of [Hermex](https://github.com/uzairansaruzi/hermex), the iPhone app, to Kotlin and Jetpack Compose, and uses the same wire protocol. Hermeh has its own design, built around a companion mascot you can name.

The phone is only the control plane. The agent, its tools and your data stay on your machine, and the app talks only to your server.

## What it does

- A companion mascot on Home with a name tag and a thought bubble. The bubble shows what your agent is working on, replies waiting for you, or whether the server is offline.
- Streamed chat with tool calls, approvals (Once, Session, Always, Deny), Stop, and a model and reasoning picker
- Sessions: search, rename, pin, mark unread, duplicate, archive, delete and Fork From Here, plus an offline copy of recent transcripts
- Active Profile and Projects filters on Home
- A workspace file browser, opened from a chat
- Tasks (scheduled cron jobs): create, edit, run now, pause, delete, run history
- Skills: browse, search, read, enable or disable
- Memory: edit notes, user profile and agent soul
- Insights: usage over 7, 30 or 90 days
- Kanban: boards, cards, comments, moves, archive, Preview Dispatch
- Bots inbox: each profile's Bot Chat with live status
- Light, dark and system themes, plus six mascot colors

## Requirements

| What | Version |
|---|---|
| Android phone or emulator | Android 9 (API 28) or later |
| Hermes agent, running `hermes dashboard` | 0.21.3 or later (tested on 0.21.5) |
| To build: JDK | 17 or later |
| To build: Android SDK | Platform 37 (Android Studio installs it) |

The legacy hermes-webui server isn't supported.

## Quick start

1. [Set up the server](#1-set-up-the-server): turn on dashboard sign-in and give the phone an address it can reach.
2. [Install the app](#2-install-the-app) from source.
3. [Connect](#3-connect): enter the address, username and password.

### 1. Set up the server

The dashboard has to require a password, and it has to be reachable from your phone. There are two ways to set it up.

**Option A: let your agent do it (easiest).** Open the app and swipe to the **Set up Hermes** page. Tap **Copy prompt**, then send the prompt to your Hermes agent on the host. The same prompt is in [`app/src/main/res/raw/setup_prompt.txt`](app/src/main/res/raw/setup_prompt.txt). The agent turns on sign-in, picks an HTTPS address, runs both checks, and replies with the address, username and password to use.

**Option B: do it by hand.**

1. **Turn on sign-in.** In the dashboard's `config.yaml`, set:

   ```yaml
   dashboard:
     basic_auth:
       username: <your-username>
       password_hash: <hash>     # see below; don't store a plaintext password
       secret: <random value>    # keeps you signed in across restarts
     public_url: <the address your phone will use>
   ```

   Generate the hash with the dashboard's own Python, from the hermes-agent checkout:

   ```sh
   python -c "from plugins.dashboard_auth.basic import hash_password; print(hash_password('<password>'))"
   ```

   Generate the secret with `openssl rand -base64 32`.

2. **Make it reachable from the phone.** Pick one:

   | Route | Steps | Address to use |
   |---|---|---|
   | **Tailscale** (recommended) | On the host, run `tailscale serve --bg 9119`. Install [Tailscale](https://play.google.com/store/apps/details?id=com.tailscale.ipn) on the phone and sign in to the same tailnet. | `https://<machine>.<tailnet>.ts.net` |
   | **Cloudflare Tunnel or a reverse proxy** | Point a hostname you own at `127.0.0.1:9119`, with TLS terminated at the tunnel or proxy. | `https://<your-hostname>` |
   | **Same Wi-Fi** (fallback) | Start the dashboard with `--host 0.0.0.0`. The password crosses your network unencrypted, so use this only on a network you trust. | `http://<host-lan-ip>:9119` |

3. **Set `dashboard.public_url`** to that exact address, including the scheme. Then restart the dashboard. Without it, the phone gets `400 Invalid Host header`.

4. **Check it.** From a machine other than the host, run:

   ```sh
   curl --fail <address>/api/status
   ```

   The response should include `"auth_required": true`, with `basic` in `auth_providers`.

### 2. Install the app

There are no prebuilt APKs yet. Build and install from source:

```sh
git clone https://github.com/dayviec25/hermeh-android.git
cd hermeh-android
./gradlew :app:installDebug          # installs on the connected phone or emulator
```

On a phone, turn on [USB debugging](https://developer.android.com/studio/debug/dev-options) first and check that `adb devices` lists it. You can also open the folder in Android Studio and press **Run**.

To build the APK file without installing it, run `./gradlew :app:assembleDebug`. It's written to `app/build/outputs/apk/debug/app-debug.apk`.

### 3. Connect

1. Open **Hermeh**. Swipe through onboarding, or tap **Already have a server?**
2. Enter the server address from step 1, plus the dashboard username and password.
3. Tap **Test Connection**, then **Connect**.

On Home, tap the mascot's name tag to rename your companion, or tap the mascot to change its color.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `400 Invalid Host header` | `dashboard.public_url` doesn't match the address you typed. Make them identical, including `https://` and the port, then restart the dashboard. |
| "Hermeh needs Hermes 0.21.3 or later" | Update hermes-agent on the host. |
| "only offers sign-in with a browser" | Add `dashboard.basic_auth` (step 1). Browser sign-in isn't supported yet. |
| Can't reach the server | Check the phone is on the same tailnet or Wi-Fi, then run the `curl` check from another device. |
| Signed out after the dashboard restarts | Set `dashboard.basic_auth.secret` to a fixed value. |
| Thought bubble says "I can't reach your server" | Hermeh lost the connection. It reconnects on its own when the network returns. |

## Develop

### Run against a local server

Run any Hermes dashboard on your computer, then forward its port into the emulator so the app can use `http://127.0.0.1:<port>`:

```sh
hermes dashboard --port 9119      # with basic_auth set as in step 1
adb reverse tcp:9119 tcp:9119
```

For repeatable runs without a real model, the [Hermex iOS repo](https://github.com/uzairansaruzi/hermex) has `scripts/local-hermes`. It runs a pinned hermes-agent with a scripted stub model on `http://127.0.0.1:9199`, with username `hermex` and password `hermex-local`. Forward it with `adb reverse tcp:9199 tcp:9199`. Every stub turn says "Let me run a quick check.", runs a `terminal` call that waits for your approval, then answers "The command returned: 1."

### Release builds

```sh
./gradlew :app:assembleRelease      # signed when keystore.properties exists
```

To sign release builds, create `keystore.properties` in the repo root. It's gitignored.

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=hermeh
keyPassword=...
```

### Layout

- `app/src/main/java/com/hermeh/android/core/network`: REST client and the gateway WebSocket (JSON-RPC, ticket auth, ping, reconnect)
- `core/data`: repositories, encrypted credentials and cookies, Room cache
- `core/designsystem`: Hermeh theme, mascot, companion sheet, shared panel chrome
- `core/model`: domain types, including the `Companion` and mascot catalog
- `feature/*`: one package per screen
- `docs/spec`: the protocol spec, live captures, design specs and data shape this app was built from. `hermeh-design.md` is the current design.

## Credits

Original design, protocol and product by [Hermex](https://github.com/uzairansaruzi/hermex) (MIT). The file-type icons are Pierre's `@pierre/trees` icons (Apache-2.0), plus six from T3 Code (MIT). Hermeh is an independent project and isn't affiliated with the Hermex or Hermes projects. It installs as `com.hermeh.android`, separate from Hermex for Android 0.1.0 builds (`com.hermex.android`).

## License

MIT. See [LICENSE](LICENSE). Third-party assets keep their own licenses, listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
