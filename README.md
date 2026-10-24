# Rogues Gallery

> [!WARNING]
> **Project discontinued.** League of Comic Geeks (LCG) will ban users who use this tool for data scraping. **Do not use Rogues Gallery with your LCG account.** This project is no longer maintained.

An experimental Android cover gallery for comics marked **Have It** in League of Comic Geeks. Android home-screen label: **Rogues Gallery**; repository: `RoguesGallery`.

Current release: **1.0.2**. [Download the signed APK](https://github.com/erickolb/RoguesGallery/releases/download/v1.0.2/RoguesGallery.apk).

## Use

1. Sign in on League's own HTTPS page inside the app.
2. Navigate to your own profile collection, then tap **Load My Collection**.
3. Wait for the owned-issue import. Collections grouped by series are expanded into their individual owned issues, with progress shown while loading.
4. The slideshow starts playing automatically (15 seconds per cover), with the cover at full size and controls hidden. Tap the cover to show or hide the title and controls floating over the image; its size stays unchanged. The title and controls fade out after 30 seconds without interaction; tap the cover to show them again. Use the double-arrow Previous/Next controls, or the Play/Pause icon and label to toggle playback. The three-dot options menu includes refresh, credits, and sign out.

Passwords are entered directly into League's WebView; the app does not read or store passwords. League session cookies remain in Android's private WebView storage until sign out. Imported titles and image URLs remain in memory; no server or Docker container is required. Android backup is disabled. Uncached covers require internet access; previously viewed covers can use the private device cache. Rotation preserves the current gallery; process termination requires reloading it.

## Version 0.2.0 settings and privacy

The app lists the full Have It collection before choosing covers. The maximum defaults to **1000** and accepts any positive whole number. Selection defaults to random; turn it off for LCG's listed order. Slideshow order is a separate random/sequential setting.

Each new session chooses a fresh selection by default. Enable **Keep the same selection between sessions** to retain eligible issue/variant references and fill gaps when comics are removed or excluded. Exclusions and saved selections are separated by LCG account. Refreshing the collection also makes a fresh selection when persistence is off.

Settings also controls the delay between covers: **3–600 seconds**, default **15**. **Cross-fade between covers** defaults on; turn it off for an instant smash cut. The current cover stays visible while the next loads. Slideshow controls form a compact, centered row with matching button heights.

After every **12 hours of active playback**, the app refreshes issue data in the background and applies the current settings and exclusions. Saved selection mode keeps eligible selected issues; fresh selection mode samples again. The gallery stays visible. If loading fails or the session expires, existing covers remain and the app retries after 15 more minutes of playback. Paused time and time outside the app do not count toward these intervals.

In the cover Menu, **Exclude this cover** removes it and fills the open slot. **Manage collection** lists the full imported collection without downloading extra covers. Check or uncheck issues to enable or exclude them, then tap Done. Enabled issues remain subject to the configured maximum.

Images are downloaded only as displayed and cached privately, with a 256 MiB / 1000-file bound. Settings includes **Clear cached covers**. The app saves settings, exclusion references, and optional selected issue references locally, and League retains its browser session cookies. It does not read, retain, or share your LCG password. See the full [Data Use / Privacy Policy](docs/privacy.html), also available from the app's top bar and slideshow menu.

## Integration status

This is an **experimental integration**, not an official League API client. The adapter requests `/comic/get_comics` with `list=2` and `list_option=issue`, following Alistair Brown's library. It supports League's current `.title` / `.cover` markup as well as the older `.comic-title` / `.comic-cover-art` format. When League returns series groups, version 0.1.3 requests each series with `series_id` while retaining the owned-collection and account filters. If necessary, it reads only the issue list on that profile's owned-collection page. Representative series covers and general series recommendations are excluded. Individual variants remain distinct.

League cover thumbnails are promoted to their `large-` image URLs, preserving the variant filename and query string. If a larger image is unavailable, the viewer falls back to its original thumbnail. A sampled League cover was 170×259 pixels as a thumbnail and 600×911 pixels in the large version; dimensions depend on the source artwork.

Selectors and collection grouping were checked against live public pages. Regression tests cover a 51-series / 188-issue collection, variants, image URL selection, and pagination. Pagination follows recognized next links or page counts; unsupported changes may require adapter updates. The 0.1.4 importer was confirmed in the signed-in Android session. Missing cover images are skipped.

The upstream `leagueofcomicgeeks` package is Node.js-specific, so it is **not a runtime dependency** of this Android application. Its collection approach is adapted into a browser script. Its old login scraping depends on UserVoice identification markup; this app instead uses League's own login. Cloudflare may reject embedded browsers; this app does not bypass challenges. If login is blocked, an approved integration or export import will be needed.

## Build

Install JDK 17 or newer and Android SDK 37, set `JAVA_HOME` and `ANDROID_HOME`, then run:

```powershell
.\android\gradlew.bat -p android :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Debug signing is for development. The **Signed Android release** workflow builds release-signed APKs and Android App Bundles using GitHub secrets. See [signed build and release instructions](RELEASING.md). Play Store publication is not automated.

## Credits

<a href="https://www.flaticon.com/free-icons/comic-book" title="comic-book icons">Comic-book icons created by Magnific - Flaticon</a>

The supplied icon is centered at 75% of Android's 72dp launcher viewport (54dp inside the 108dp adaptive foreground canvas) on LCG navy `#101F3E`.

Collection adapter based on [leagueofcomicgeeks by Alistair Brown](https://github.com/alistairjcbrown/leagueofcomicgeeks), copyright 2017 Alistair Brown, MIT license. The full upstream license is bundled in the app and shown in Credits.

League of Comic Geeks and comic cover artwork belong to their respective owners. This app is unofficial and unaffiliated.

## Website

The public website is [erickolb.github.io/RoguesGallery](https://erickolb.github.io/RoguesGallery/). GitHub Pages publishes the static files in `docs/` from `main`; no Node build, backend, or container is needed.

The site follows Cogsworth's layout with LCG navy `#101F3E`, feature descriptions, setup, privacy, and credits. The main photo and six app screenshots are stored in `docs/assets/`. Screenshots open in a full-size viewer; each has descriptive alt text.

The website links to the latest signed release APK and includes an Obtainium button that adds `https://github.com/erickolb/RoguesGallery` as the update source. Keep the policy in `docs/privacy.html` and the app's `android/app/src/main/assets/privacy.html` consistent when changing data practices. Push website changes to `main` to update GitHub Pages.
