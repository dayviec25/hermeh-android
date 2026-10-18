# Hermeh for Android

A native Android client for a self-hosted [Hermes agent](https://github.com/NousResearch/hermes-agent) dashboard. It started as a port of [Hermex](https://github.com/uzairansaruzi/hermex), the iPhone app, to Kotlin and Jetpack Compose, and speaks the same wire protocol. Hermeh has its own design, built around a companion mascot.

The phone is only the control plane. The agent, its tools and your data stay on your machine. The app talks only to your server.

## What it does

- Streamed chat with tool calls, approvals (Once, Session, Always, Deny), Stop, and a model and reasoning picker
- Sessions: search, rename, pin, mark unread, duplicate, archive, delete, Fork From Here, plus an offline copy of recent transcripts
- Active Profile and Projects on Home
- A workspace file browser, opened from a chat
- Tasks (scheduled cron jobs): create, edit, run now, pause, delete, run history
- Skills: browse, search, read, enable or disable
- Memory: edit notes, user profile and agent soul
- Insights: usage over 7, 30 or 90 days
- Kanban: boards, cards, comments, moves, archive, Preview Dispatch
- Bots inbox: each profile's Bot Chat with live status
- Light, dark and system themes, plus logo color presets

It supports the Hermes dashboard (`hermes dashboard`), version 0.21.3 or later. The legacy hermes-webui server isn't supported.

## Set up the server

1. On the host, turn on username and password sign-in for the dashboard (`dashboard.basic_auth`) and set a persistent `dashboard.basic_auth.secret`. Then set `dashboard.public_url` to the address your phone will use. The app's onboarding has an Agent prompt page with a prompt you can paste to your agent to do this.
2. Make the dashboard reachable from the phone. Pick one:
   - **Tailscale (recommended):** run `tailscale serve --bg 9119` on the host and install Tailscale on the phone. Then use the `https://<machine>.<tailnet>.ts.net` URL.
   - **Cloudflare Tunnel or a reverse proxy** that terminates TLS on a hostname you own.
   - **Same Wi-Fi:** use `http://<host-lan-ip>:9119`. This needs the dashboard to listen on that address, so keep the password strong.
3. Check it from another machine: `curl <url>/api/status` should report `"auth_required": true`.

## Install

Download `hermeh-<version>.apk` to the phone, open it and allow installs from that source. Or install it with adb:

```sh
adb install -r dist/hermeh-0.1.0.apk
```

Open Hermeh, tap **Already have a server?**, and enter the URL, username and password.

## Build from source

You need JDK 17 or later and the Android SDK with platform 37.

```sh
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/
./gradlew :app:assembleRelease      # signed when keystore.properties exists
```

To sign release builds, create `keystore.properties` in the repo root. It's gitignored.

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=hermeh
keyPassword=...
```

## Develop against a local host

The iOS repo's `scripts/local-hermes` runs the pinned hermes-agent with a scripted stub model on `http://127.0.0.1:9199`, with username `hermex` and password `hermex-local`. Forward it into the emulator:

```sh
adb reverse tcp:9199 tcp:9199
```

Every stub turn says "Let me run a quick check.", runs a `terminal` call that waits for your approval, then answers "The command returned: 1."

## Layout

- `app/src/main/java/com/hermeh/android/core/network`: REST client and the gateway WebSocket (JSON-RPC, ticket auth, ping, reconnect)
- `core/data`: repositories, encrypted credentials and cookies, Room cache
- `core/designsystem`: Hermeh theme, mascot, wordmark, shared panel chrome
- `feature/*`: one package per screen
- `docs/spec`: the protocol spec, live captures, design spec and data shape this port was built from

## Credits

Original design, protocol and product by [Hermex](https://github.com/uzairansaruzi/hermex) (MIT). The file-type icons are Pierre's `@pierre/trees` icons (Apache-2.0), plus six from T3 Code (MIT). This is an independent port and isn't affiliated with the Hermex or Hermes projects. Hermeh installs as `com.hermeh.android`, separate from Hermex 0.1.0 builds (`com.hermex.android`).

## License

MIT. See [LICENSE](LICENSE). Third-party assets keep their own licenses, listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
