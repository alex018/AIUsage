# AIUsage: notes for contributors and coding agents

Android app and home-screen widget that show how much of your AI subscription limits you have used
(Claude Code, ChatGPT Codex and Gemini: a short window and a weekly one each). A fourth service, DeepSeek
(account balance), is planned.

## How it gets the numbers
- You sign in **on the real claude.ai / chatgpt.com / gemini.google.com** inside the app (`LoginActivity`, a WebView).
- A hidden WebView (`WebFetcher`) runs a `fetch()` against the site's own pages with that sign-in's cookies.
  Plain HTTP gets `403` from Cloudflare, so do not replace this with OkHttp.
- Claude: `/api/organizations`, then `/api/organizations/{id}/usage`.
  ChatGPT: `/api/auth/session` (gives an access token), then `/backend-api/wham/usage`.
  Gemini: the page `gemini.google.com/usage`; its text is split at the "Current usage" and "Weekly limit" headings
  (the weekly reset line is above its percent). `Parsers.parseReset` understands several wordings of the reset time.
- **Do not use the OAuth tokens of the Claude Code or Codex command-line tools.** Anthropic's consumer terms forbid
  them outside their own apps, and refreshing such a token on a phone would log the computer's tool out (tokens rotate).
- All of this is undocumented and can change at any time. If a service breaks, fix its parser in `Parsers.kt` first.
  The in-app diagnostic log (Copy log) keeps a short copy of the Gemini page text to help with that.
- Google may block sign-in inside a WebView.

## Layout (`app/src/main/java/app/aiusage/`)
| File | Job |
|---|---|
| `Providers.kt` | `Provider` enum: login URL, cookie check, JS to run, parser. **Add a service here.** |
| `Parsers.kt`, `Model.kt`, `Format.kt` | Pure Kotlin (page or JSON to `Snapshot`), unit-tested |
| `Pace.kt` | "Even pace" (share of the window that has passed), status colours, runs-out time. Unit-tested |
| `WebFetcher.kt` | Hidden WebView that runs the JS |
| `Repo.kt`, `Store.kt` | Refresh all services; keep the last numbers and errors |
| `RefreshWorker.kt` | WorkManager: every 15 minutes, and on demand |
| `WidgetDraw.kt` | The ten widget looks, each drawn as one bitmap with Canvas (`WidgetStyle`), for 1 to 4 services |
| `WidgetModel.kt` | Stored numbers (or demo numbers) to what the looks draw |
| `UsageWidget.kt`, `WidgetConfigActivity.kt` | The widget (one ImageView; tap = open the app, top-right = refresh) and its look screen |
| `MainActivity.kt`, `LoginActivity.kt`, `Looks.kt` | The app screen, the sign-in WebView, shared UI |
| `Diag.kt` | Small log of what happened (no passwords, cookie values or page contents except the Gemini usage text) |

## Commands
| What | Command |
|---|---|
| Tests | `./gradlew :app:testDebugUnitTest` |
| Build | `./gradlew :app:assembleDebug` (needs JDK 17 or newer and the Android SDK) |
| Install | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| Open | `adb shell am start -n app.aiusage.debug/app.aiusage.MainActivity` |

Toolchain: Android Gradle Plugin 9.5 (it compiles Kotlin itself: do not add the `kotlin-android` plugin), Kotlin 2.4,
compileSdk 37, minSdk 26.

## Releases
Bump `versionName` and `versionCode` in `app/build.gradle.kts` for every new build; the app shows its version at the
bottom of its screen.

## Gotchas
- A widget layout (RemoteViews) may only use allowed view classes. A plain `<View>` makes the whole widget show
  "Can't load widget". Use `ImageView`, `FrameLayout`, `TextView`, etc.
- Every look handles 1 to 4 services (`WidgetStyle.refH(n)`; three and four use tighter layouts). Debug builds can
  draw all looks into one picture:
  `adb shell am start -n app.aiusage.debug/app.aiusage.GalleryActivity --ei n 4` (add `--ei more 90` for a taller widget), then
  `adb exec-out run-as app.aiusage.debug cat files/gallery_4.png > gallery.png`.
- Debug builds can draw made-up numbers on the widget:
  `adb shell am broadcast -n app.aiusage.debug/app.aiusage.UsageWidget -a app.aiusage.DEMO --ez on true --ei n 4`
  (`--ez on false` to stop).
