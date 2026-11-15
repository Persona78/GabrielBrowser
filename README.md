# Gabriel Browser

![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![Engine](https://img.shields.io/badge/engine-GeckoView%20160-FF7139?logo=firefoxbrowser&logoColor=white)
![ABI](https://img.shields.io/badge/ABI-arm64--v8a-blue)
![Languages](https://img.shields.io/badge/UI-6%20languages-green)

**A light, private Android browser built on GeckoView (the Firefox engine).**

Gabriel Browser blocks ads, trackers, pop-ups and silent redirects, forces HTTPS, resolves DNS over HTTPS only inside the app, and gives you a clean, touch-friendly interface with bottom sheets, cards and big buttons.

> **Why "Gabriel"?** The name honors the *São Gabriel*, the flagship of Vasco da Gama's fleet on the first voyage by sea from Europe to India (1497–1499). A browser is an explorer too.
>
> **The icon** is a golden **armillary sphere** on deep green with a red Earth at its center, the emblem of the Portuguese Age of Discovery and a symbol on Portugal's flag. A single-color version is included for Android 13+ themed icons.

---

## Table of contents

1. [Features](#1-features)
2. [Requirements and installation](#2-requirements-and-installation)
3. [User manual](#3-user-manual)
4. [Keeping it up to date](#4-keeping-it-up-to-date-important)
5. [Privacy and security notes](#5-privacy-and-security-notes)
6. [Troubleshooting](#6-troubleshooting)
7. [Known limitations](#7-known-limitations)
8. [Project structure](#8-project-structure)
9. [Credits and licenses](#9-credits-and-licenses)

---

## 1. Features

### Protection
- **DNS over HTTPS (DoH) only inside the app.** Default server: `https://freedns.controld.com/p2` (Control D, blocks malware and ads). It is editable (empty = system DNS). Other apps on the phone are not affected.
- **Strict tracking protection** (Enhanced Tracking Protection, strict level, with cross-site cookie isolation).
- **HTTPS-only mode.** Plain `http://` pages are not loaded unless you explicitly choose to continue.
- **Safe Browsing** is requested from the engine (see [Known limitations](#7-known-limitations)).
- **uBlock Origin built in** for ad and tracker blocking.
- **Pop-up blocking** and **blocking of script redirects** to another site that happen without a tap.
- **External schemes blocked** (`intent://`, `market://`, `file://`, and similar), so pages cannot launch other apps or read local files.
- **Site permissions on your terms.** Location, camera and microphone are only used after you tap **Allow** on a clear panel; notifications, VR and sound autoplay are always blocked.
- **File check on every download.** The app reads the first bytes of each file and warns you if a `.jpg` is not really a JPEG, an `.mp4` is not really an MP4, or a program is disguised as another file.
- **Insecure-connection warning** for `http://` sites, with an optional **automatic VirusTotal scan**.

### Browsing
- Address bar with a lock icon, a **shield button** with a tracker counter and a **protection panel**.
- Bottom bar: back, forward, home, **tabs** (with a counter), reload/stop, menu.
- **Multiple tabs** (up to 12): open, switch and close tabs; links that open a new window after a tap open in a new tab.
- **Bookmarks** and **history** (history can be switched off).
- **Private mode** (no history or persistent cookies, hidden from the recent-apps screen, screenshots blocked).
- **Desktop site** switch.
- **Media detector:** a **⬇ button** appears in the address bar when the page has videos, audio or large images that can be saved; tap it for a list with a download button for each.
- **Long press** on images, videos, audio and links: save, copy, open, share.
- **Custom home page** and **eight search engines** (DuckDuckGo Lite, DuckDuckGo, Brave Search, Startpage, Google, GIBIRU, Bing, Swisscows).
- **Extensions window**: install from addons.mozilla.org, from an `.xpi` link, or with a shortcut to Malwarebytes Browser Guard; enable, disable or remove.
- **Clear browsing data** (cookies, cache, site data, permissions, bookmarks, history) and **clear data for the current site only**.
- Share or copy links **without tracking parameters** (`utm_*`, `fbclid`, `gclid`, and more).
- Downloads (saved to the Downloads folder, or to a **folder you choose**), file uploads, full-screen video, and automatic recovery if a page crashes.
- **Web page dialogs:** JavaScript `alert` / `confirm` / `prompt`, drop-down lists (`<select>`), and HTTP sign-in prompts work.
- Can be set as the **default Android browser**.

### Interface
- Light and dark themes that follow the system, with green as the accent color.
- **Six languages:** Portuguese (Portugal), Portuguese (Brazil), English (US), English (UK), Spanish and French. Automatic by system language, or choose one in **Menu > Language**.

---

## 2. Requirements and installation

- **Phone:** Android 10 or newer (API 29), 64-bit ARM (arm64-v8a). Most phones from the last several years qualify.

### Install
Download the APK from the **Releases** page of this repository, open it on the phone and allow installation from that source when Android asks. To update, install the new APK **over** the old one: it must be signed with the **same key** and have a higher `versionCode`, otherwise Android refuses the update (or asks you to uninstall first, which erases the app's data).

### Build from source
You need a recent **Android Studio** (Panda 3 / 2025.3.3 or newer, because of `compileSdk` 37) with JDK 17. The project uses AGP 9.1.1 and Gradle 9.3.1, which Android Studio downloads by itself.

1. Open the project folder in Android Studio and let Gradle sync.
2. Fetch uBlock Origin into `app/src/main/assets/ublock`: `./scripts/update-ublock.sh` (on Windows use Git Bash or WSL: `bash scripts/update-ublock.sh`). Without it the app still runs, just without ad blocking from uBlock.
3. Optional: pin the latest GeckoView with `./scripts/update-geckoview.sh --apply`.
4. Run on a phone (USB debugging) or use **Build > Build APK(s)**. The APK is in `app/build/outputs/apk/`.

**Signing (do it once, keep the file safe).** Copy `keystore.properties.example` to `keystore.properties`, fill it in, and create the key:
```
keytool -genkeypair -v -keystore gabriel.jks -alias gabriel -keyalg RSA -keysize 4096 -validity 10000
```
Both files are git-ignored. Always sign with the same key so updates install over the old app.

If a GeckoView method fails to compile, the API names change between versions: check the [Javadoc](https://mozilla.github.io/geckoview/javadoc/mozilla-central/) for the version in `gradle.properties`.

---

## 3. User manual

### 3.1 The main screen

| Area | What it does |
|---|---|
| **Site icon** (left of the address) | A **lock** means the page uses HTTPS. A magnifier means no page or a non-secure page. |
| **Address bar** | Type a web address or a search. A word without a dot is searched with your search engine; something like `example.com` opens as `https://example.com`. |
| **✕ button** | Clears the address bar text. |
| **⬇ button** | Appears only when the page has saveable media (see 3.8b). |
| **Shield** | Shows how many trackers the engine blocked on this page. Tap it to open the **protection panel**. |
| **Progress bar** | Shows page loading. |
| **Bottom bar** | **Back**, **Forward**, **Home**, **Tabs** (the number is how many are open), **Reload / Stop**, **Menu**. |

**Tabs**
- Tap the **tabs button** (the small square with a number) to see all open tabs as cards. Tap a card to switch to that tab, tap **✕** to close it, or tap **New tab**.
- **Long press** the tabs button to open a new tab right away.
- The Back button goes back in the page; when a tab has nothing to go back to and other tabs exist, Back closes it.
- Up to 12 tabs can be open. Each tab keeps its own page, tracker counter and loading state.
- A link that opens a "new window" after you tap it opens in a new tab. Pop-ups that appear without a tap are blocked.
- Turning **Private** mode on or off closes all tabs and starts one fresh tab.
- Tabs are not restored when the app is closed.

### 3.2 The menu

Tap the menu button (bottom right) to open a bottom sheet of tiles. Tiles that are switched on are highlighted in green.

| Tile | What it does |
|---|---|
| **New tab** | Opens a new tab on your home page. |
| **Tabs** | Lists open tabs. |
| **☆ Bookmark / ★ Bookmarked** | Adds or removes the current page from your bookmarks. |
| **Bookmarks** | Opens the bookmark list. |
| **History** | Opens the history list and its on/off switch. |
| **Share** | Shares the current link **without tracking parameters**. |
| **Copy link** | Copies the current link without tracking parameters. |
| **Protection** | Shows what is protecting you on this page. |
| **Folder** | Chooses where downloads are saved. |
| **Clear data** | Deletes browsing data you choose. |
| **Private** | Turns private mode on or off. |
| **Desktop site** | Requests the desktop version of sites. |
| **Extensions** | Manages extensions. |
| **Home page** | Sets your home page. |
| **Search** | Chooses the search engine. |
| **VirusTotal** | Sets up automatic scanning of suspicious sites. |
| **DNS** | Sets the DNS-over-HTTPS server. |
| **Language** | Chooses the app language. |
| **About** | Version, a short description and a shortcut to **VirusTotal**. |

### 3.3 Bookmarks

- Open a page, then **Menu > Bookmark** to save it. The tile changes to **★ Bookmarked**; tap it again to remove the bookmark.
- **Menu > Bookmarks** shows each bookmark as a card with a letter, name and address. **Tap a card to open it.** Tap **✕** to delete it.
- Bookmarks are stored only on your phone.

### 3.4 History

- **Menu > History** lists the 60 most recent pages (up to 300 are kept; older ones are dropped). Each page appears once, with its latest visit on top.
- **Save history** switch: turn it off and the app stops recording pages. Existing entries are **not** deleted when you switch it off.
- Tap a card to open the page, tap **✕** to remove one entry, or **Clear history** to remove everything.
- History is **never recorded in private mode**.

### 3.5 Private mode

- **Menu > Private** turns it on or off. Switching starts a fresh session and goes to your home page.
- While it is on: the engine keeps no history and no persistent cookies, the app's history is not recorded, the address bar shows "🕶 Private", the app is hidden from the recent-apps screen, and **screenshots are blocked**.
- The app always starts in normal mode.
- Private mode does **not** hide your activity from your internet provider or DNS server.

### 3.6 Clear data

**Menu > Clear data** lets you tick what to delete:
- **Cookies**, **Cache** (temporary files and images) and **Site data** (local storage): ticked by default.
- **Site permissions and settings**: unticked by default.
- **Bookmarks**: unticked by default.
- **History**: ticked by default.

Tap **Clear**. The app then returns to your home page.

To clear **only the site you are on**, open the shield (**Protection**) and tap **Clear site data**.

### 3.7 Protection panel

Tap the shield or **Menu > Protection** to see, for the current page: connection type (HTTPS or none), trackers blocked, whether uBlock Origin is active, pop-up and redirect blocking, and the DNS in use. The tracker counter counts the engine's blocks only; uBlock Origin's own blocks are not included.

### 3.8 Long press on images, videos, audio and links

Touch and hold on:
- **An image, video or audio:** **Save** (to the Downloads folder, with an Android notification), **Copy file link**, **Open file**.
- **A link:** **Open link**, **Copy link** (tracking parameters removed), **Share link**.

Notes:
- Many video sites (YouTube and other streaming platforms) deliver video in segments or protected streams, not as one file. For those, the app says the content cannot be saved. Direct files such as `.mp4` or `.jpg` work.
- Only **https** links are saved this way. These downloads (from a long press or the ⬇ button) are fetched by Android or by the app itself, **outside the browser engine**: they use the system DNS and do not share the browser's sign-in, so a file that needs a login may fail. Downloads you start by **tapping a download link on a page** do share the session and the in-app DNS (see 3.8c).

### 3.8b Media detector (download button)

- When a page contains **videos, audio or images** that exist as direct files, a **⬇ button with a number** appears in the address bar, left of the shield.
- Tap it to open **Media on this page**: a card per file (🎬 video, 🎵 audio, 🖼 image) with its name, size and site. **Tap a card to save the file** to the Downloads folder. The **🔗** button copies the file's link.
- Videos are listed first, then audio, then images (largest first). Images smaller than 200×200 pixels (icons, buttons) are ignored. Up to 150 files are tracked per page and 60 are shown.
- The list resets when a new page loads, and each tab has its own list.
- It works with an extension built into the app (**Gabriel Media Detector**, visible in **Menu > Extensions**). It runs only on your phone and sends nothing anywhere; you can switch it off there.
- **Limits:** videos delivered in segments or behind protection (YouTube, most streaming sites, `blob:` videos, HLS/DASH) have no single file to save and do not appear. Some sites only allow downloads while signed in. Detection may not work in private mode.

### 3.8c Download folder

**Menu > Folder** sets where every download is saved (long press, the ⬇ button and normal downloads).
- **Downloads** (default): Android's Downloads folder.
- **Choose another folder…** opens Android's folder picker; pick any folder on the phone or on the SD card and the app remembers it.
- **Tapping a download link on a page:** the browser engine fetches the file (with the page's sign-in and the in-app DNS) and the app saves it, in Downloads or in your chosen folder. There is no progress notification, only a message when it finishes.
- **Long press and the ⬇ button:** in Downloads you get Android's normal download notification; in a chosen folder the app downloads it itself (no progress notification). Only **https** links; if the chosen folder is no longer available it falls back to Downloads.
- Files with the same name are not overwritten; Android adds a number.

### 3.8d File check (does the file match its extension?)

After every download the app reads the first bytes of the file and compares them with the extension. A `.jpg` must really start like a JPEG, an `.mp4` must really be an MP4 container, and so on (JPEG, PNG, GIF, WebP, BMP, MP4/MOV/HEIC, WebM/MKV, OGG, FLAC, MP3, AAC, WAV, AVI, PDF, ZIP/APK/Office, GZIP, RAR, 7-Zip and more).
- If they do not match, a **Suspicious file** panel shows the extension and the real content, with **Delete** and **Keep**.
- A program (Windows EXE, Linux ELF, Android DEX or a script) hiding under another extension is shown as **Dangerous file**.
- A file that is really HTML (a login or error page) saved as an image, video or archive is also reported, as is an empty file.
- Limits: only the start of the file is checked. It does not prove the file is complete or harmless, and plain text or unknown formats are not flagged. Use VirusTotal for a deeper analysis.

### 3.8e Site permissions

When a site asks for the **location**, the **camera** or the **microphone**, the app shows an **Allow / Block** panel. Android's own permission dialog follows the first time.
- Camera and microphone are remembered for that site only until the app is closed. Location is remembered by the engine; to forget it use **Menu > Clear data > Site permissions and settings**.
- Notifications, VR and sound autoplay are always blocked.

### 3.8f Web page dialogs and sign-in

Pages can show their own dialogs and the app handles them with standard Android dialogs: **alert** (OK), **confirm** (OK / Cancel), **prompt** (text box), **drop-down lists** (`<select>`, single or multiple choice) and **HTTP sign-in** (user name and password, for example a router). Closing a dialog with Back or by tapping outside counts as Cancel. **File upload** opens Android's file picker. Date, time and color pickers are not supported yet.

### 3.9 Extensions

**Menu > Extensions** lists installed extensions as cards.
- **Switch:** enable or disable an extension.
- **🗑:** remove an extension. The built-in uBlock Origin cannot be removed.
- **Add extension** opens three choices:
  1. **Extension store** (addons.mozilla.org): browse, open an extension, and tap its direct **download link (.xpi)**. The app then asks you to confirm.
  2. **Malwarebytes Browser Guard:** downloaded from the official Firefox add-ons site at install time (it is not bundled because of its license).
  3. **Install from link:** paste a direct `https://…xpi` link.
- Every installation shows the **permissions** the extension requests and installs only after you tap **Add**.
- Only install extensions you trust.
- The store's own **"Add to Firefox"** button probably does not work in an embedded browser like this; use the `.xpi` download link instead. Use **Desktop site** if you only see the mobile listing.
- Only extensions compatible with GeckoView on Android work. Browser Guard's compatibility is not confirmed.
- **Tip:** uBlock Origin already blocks ads. If you install Browser Guard, turn off its ad blocking and keep its malware and phishing protection to avoid overlap and slowdowns.

### 3.10 Insecure sites and VirusTotal

When you open an `http://` page (no encryption), the app shows **"This site does not have a secure connection"** once per address.

- **Without an API key:** a sheet explains the risk. **Scan with VirusTotal** copies the address to your clipboard and opens VirusTotal's URL page; paste the address and scan. (VirusTotal opens in a **new tab**, so the site stays open in the previous one.)
- **With an API key (automatic scan):**
  1. Create a free account at virustotal.com and copy your API key (Profile > API key).
  2. **Menu > VirusTotal:** paste the key, switch on **Automatically scan sites without a secure connection**, tap **Save**.
  3. From then on, insecure sites are scanned automatically. If no engine flags the site, you see a short message ("No threats detected"). If any engine flags it, a **⚠ Suspicious site** sheet shows how many engines flagged it, with **Leave site**, **View report** and **Continue at my own risk**.
  4. **Scan this page** in the same menu checks the current page on demand, including HTTPS pages.
  5. You can reach the same settings from **Menu > About**, using the **VirusTotal** card.
- **Privacy:** the address is sent to VirusTotal only when the option is on or when you tap **Scan**. The key is stored only on your phone. The app never ships with a key.
- **Limits:** the free API has per-minute and daily limits; the app tells you when one is reached.
- This request uses the system DNS, not the in-app DoH server (only the browser engine uses DoH).

### 3.11 Search engines

**Menu > Search** shows a card for each engine with a short description. The current one is marked ✓.

| Engine | Notes |
|---|---|
| **DuckDuckGo Lite** | Light, distraction-free version (default). |
| **DuckDuckGo** | Private search that does not track you. |
| **Brave Search** | Its own independent index, focused on privacy. |
| **Startpage** | Google results, anonymously. |
| **Google** | Most widely used; collects search data. |
| **GIBIRU** | Google-based results without tracking. |
| **Bing** | Detailed results from Microsoft. |
| **Swisscows** | Swiss search, good for music (SoundCloud); excludes malicious and adult content. |

Each engine also supplies the default home page unless you set your own.

### 3.12 Home page

**Menu > Home page:** type an address (the app adds `https://` if missing), or tap **Use current page**. Leave it empty to use your search engine's page. It opens when the app starts and when you tap the **Home** button.

### 3.13 DNS (DoH)

**Menu > DNS:** edit the DoH server URL. Default: `https://freedns.controld.com/p2`. **Reset to p2** restores it. Leaving it empty uses the system DNS. The change applies after the app **restarts** (it restarts itself when you save). The DNS is used only by this app.

### 3.14 Language

**Menu > Language:** choose **Automatic** (follows the system; falls back to English if unsupported) or one of the six languages. Text without a translation appears in Portuguese.

### 3.15 Setting it as the default browser

In Android **Settings > Apps > Default apps > Browser app**, choose **Gabriel Browser**. Links from other apps will then open here.

---

## 4. Keeping it up to date (important)

In a self-built browser, **security depends on you updating the engine.**

### GeckoView: every ~4 weeks, or on any security fix
- Mozilla releases a new version every 4 weeks. Fixes are listed at [mozilla.org/security/advisories](https://www.mozilla.org/security/advisories/).
- Check: `./scripts/update-geckoview.sh` (exits with code 10 if a newer version exists).
- Apply: `./scripts/update-geckoview.sh --apply`, then build, test, and install **over** the old app using the **same signing key**. The script also bumps `versionCode`.

### uBlock Origin: about once a month
- Filter lists update by themselves; the extension does not. Run `./scripts/update-ublock.sh` (it also bumps `versionCode` when the uBlock version changed) and rebuild.
- In uBlock's dashboard you can enable the **Cookie notices / Annoyances** lists to hide cookie banners.

### Android build tools
Android Studio will notify you of AGP and Gradle updates. Update when the project builds cleanly.

### Reminder
Set a monthly reminder to run both scripts. If you host the project on GitHub, **Obtainium** can notify you of new APKs.

---

## 5. Privacy and security notes

- **Local data:** bookmarks, history, settings and the optional VirusTotal key are stored only in the app's private storage on your phone. Android backup is disabled for the app.
- **Network:** no analytics or telemetry were added. Page loads go through the engine with strict tracking protection and in-app DoH. The only extra requests are the ones you trigger: extension downloads, long-press/⬇ downloads and VirusTotal scans (these use the system DNS, not the in-app DoH).
- **Downloads:** file names are sanitized, links from other apps are opened only if they are `http(s)`, and every download is checked against its extension (see 3.8d).
- **No bundled keys:** the app contains no API keys.
- **Permissions:** the manifest declares Internet and network state, plus camera, microphone, audio settings and location. The last four are only used **after you tap Allow** on a website's request and then accept Android's own dialog; the app never asks for them by itself. Notifications are never allowed.
- **Antivirus warnings:** a new APK signed by you and installed outside a store may be flagged as "unknown" (a common false positive). If your antivirus names a specific threat, check the exact alert text.
- **Optional hardening:** setting `privacy.resistFingerprinting` in `about:config` increases protection but can break some sites.

---

## 6. Troubleshooting

| Problem | What to try |
|---|---|
| **uBlock Origin shows "not loaded"** | Run `./scripts/update-ublock.sh` and rebuild; check `app/src/main/assets/ublock/`. |
| **A site will not open** | HTTPS-only mode blocks plain `http://`. Choose to continue on the warning page if you trust the site. Also try **Desktop site**. |
| **A page or site breaks** | A strict setting may be the cause: try the site in a normal session, disable an extension, or clear data for that site. |
| **DoH is not working** | Check the DoH URL in **Menu > DNS**. Optionally set `network.trr.bootstrapAddr` = `76.76.2.2` in `about:config` so the server name does not need the system DNS the first time. |
| **VirusTotal says "invalid API key"** | Re-copy the key from your VirusTotal profile into **Menu > VirusTotal**. |
| **VirusTotal limit reached** | The free API has per-minute and daily limits; try again later. |
| **Cannot save a video** | It is probably a segmented or protected stream (see [3.8](#38-long-press-on-images-videos-audio-and-links)). |
| **Update will not install over the old app** | It must be signed with the **same key** and have a **higher `versionCode`** (`app/build.gradle.kts`; the update scripts raise it for you). A lower or equal `versionCode` makes Android say the app is already up to date. |
| **Camera, microphone or location does not work on a site** | Tap **Allow** on the panel, then accept Android's dialog. If you refused Android's dialog before, enable the permission in Android **Settings > Apps > Gabriel Browser > Permissions**. |
| **A login or payment page says "Redirect blocked"** | The app blocks script redirects to another site that happen without a tap. Tap the sign-in button again, or try the site in **Desktop site** mode. Please report the site. |
| **Extension does not install** | It may not be compatible with GeckoView on Android, or the link is not a direct `.xpi` link. |

---

## 7. Known limitations

- **Not supported yet:** date, time and color pickers on web pages.
- **Safe Browsing:** a self-built GeckoView may need a Google API key for Safe Browsing. It is not confirmed whether it is active in this build; you can test with the Safe Browsing test pages (`testsafebrowsing.appspot.com`). No key is bundled.
- **DoH bootstrap:** in DoH-only mode, the DoH server's own name is resolved once by the system DNS to start up (see Troubleshooting for the optional fix).
- **Tracker counter:** counts only the engine's blocks, not uBlock Origin's.
- **Extensions:** only GeckoView-compatible extensions work; Malwarebytes Browser Guard's compatibility is unconfirmed.
- **Downloads:** media delivered in segments or behind a login cannot be saved. Long-press and ⬇ downloads, and VirusTotal, use the system DNS and do not share the browser's session; downloads started by tapping a link do.
- **Permissions:** camera and microphone approvals last only until the app is closed; there is no per-site permission manager yet (use **Clear data > Site permissions and settings**).
- **Redirect blocking:** a script redirect to another site without a recent tap is blocked, which can interfere with some sign-in or payment flows.
- **File check:** it looks only at the first bytes of a file, so it cannot detect a truncated or malicious file with a correct header.
- **Testing:** this project was assembled without a full on-device test run for every feature. If a method fails to compile or a feature misbehaves, please report the exact error.

---

## 8. Project structure

```
GabrielBrowser/
├── README.md                  This file
├── build.gradle.kts           Plugin versions (AGP 9.1.1)
├── settings.gradle.kts        Repositories (including maven.mozilla.org)
├── gradle.properties          GeckoView version
├── keystore.properties.example
├── scripts/
│   ├── update-geckoview.sh    Check/apply the latest GeckoView
│   └── update-ublock.sh       Download the latest uBlock Origin
└── app/
    ├── build.gradle.kts       App module (minSdk 29, arm64-v8a only)
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/ublock/     uBlock Origin files (after running the script)
        ├── assets/media/      Gabriel Media Detector (built-in extension)
        ├── java/pt/gabriel/browser/
        │   ├── MainActivity.kt   Browser logic, menu and dialogs
        │   ├── Sheet.kt          Bottom-sheet UI components
        │   ├── FileCheck.kt      File type check (magic bytes)
        │   ├── I18n.kt           Translations (6 languages)
        │   └── VirusTotal.kt     VirusTotal API client
        └── res/               Layout, colors, themes, icon, drawables
```

**Adding a translation:** open `I18n.kt` and add a line, with the Portuguese source text exactly as it appears in the code:
```kotlin
r("texto em português", "English", "Español", "Français")
```
Use `{}` for a variable part (a name or number). Optional extra arguments give the Brazilian Portuguese and US English variants (`br = "..."`, `us = "..."`).

---

## 9. Credits and licenses

- **GeckoView** and the Firefox engine: © Mozilla, under the Mozilla Public License 2.0.
- **uBlock Origin:** © Raymond Hill, GPLv3. It is downloaded from the official releases by the script; check its license before redistributing the APK.
- **Malwarebytes Browser Guard:** proprietary; it is never bundled, only installed by the user from addons.mozilla.org.
- **VirusTotal:** a service by Google; use of its API is subject to its terms.
- **Control D** (default DNS) and the listed search engines are third-party services with their own terms and privacy policies.
- **Gabriel Browser's own code:** GNU General Public License v3.0 (see the `LICENSE` file). This matches uBlock Origin's license (GPLv3) and is compatible with GeckoView's MPL 2.0. uBlock Origin is not stored in the repository (the `assets/ublock` files are git-ignored and fetched by the script).
- The *São Gabriel* dedication and the armillary-sphere icon honor Portugal's maritime history.

*Built with care for people who like to explore the web privately.*
