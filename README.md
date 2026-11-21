# AIUsage

An Android home-screen widget that shows how much of your AI subscription limits you have used:
**Claude Code**, **ChatGPT Codex** and **Gemini** (a short window and a weekly one each), so you can pace yourself.
A fourth service, DeepSeek balance, is planned.

> **Unofficial.** Not affiliated with, endorsed by or supported by Anthropic, OpenAI or Google. "Claude", "ChatGPT",
> "Codex" and "Gemini" are trademarks of their owners and are used only to say what the app works with.
> It reads the same pages you can open in a browser, through interfaces that are **not documented** and can change at any
> time, so it may stop working until it is updated. Use it at your own risk and follow each service's terms.

## What it does
- Shows percent used, when each limit resets, and a status: **On track**, **A bit fast** or **Too fast**.
- Draws a dotted **even pace** mark on every bar: where you would be if you used the limit evenly over its window
  (a week is about 14% a day). A bar past the mark means you are using it faster than average.
- Ten widget looks (big numbers, rings, traffic lights, batteries, one line, countdown, colour tiles, gauges, columns,
  one number). Each looks right with one to four services.
- Every widget on your home screen has its own look and its own services, so you can have several different ones.
- A big refresh button on the widget with clear feedback when you tap it.

## How it works
You sign in on the real sites inside the app. A hidden WebView then asks each site's own pages for your usage,
the same way the site's usage page does, and the numbers are drawn onto the widget. Nothing leaves your phone.
Details are in [`CLAUDE.md`](CLAUDE.md).

## Privacy
- The app talks only to claude.ai, chatgpt.com and gemini.google.com (and Google's sign-in). There is no server of
  ours, no analytics and no ads.
- The app never sees your password. It keeps only the sign-in cookies the sites set, in the WebView's private storage
  on your phone. **Disconnect** deletes them.
- Permissions: internet, network state and vibrate (a short buzz when you tap refresh).
- A small diagnostic log stays on the phone. You can copy or clear it from the app.

## Build
You need JDK 17 or newer and the Android SDK (platform 37).

```bash
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # APK in app/build/outputs/apk/debug/
```

Install the APK, open **AI Usage**, tap **Connect** for each service, then add the widget from the home screen's widget
list (or from the button in the app) and pick a look.

## Contributing
Issues and pull requests are welcome. The most useful ones: a service whose page changed (fix its parser in
`Parsers.kt`, with a test), a new service (`Providers.kt`) and new widget looks (`WidgetDraw.kt`).

## License
[MIT](LICENSE)
