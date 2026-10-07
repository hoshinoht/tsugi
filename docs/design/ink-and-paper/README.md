# Ink & Paper (Direction B)

The brand direction for Tsugi's own look. Washi paper, sumi ink, indigo service badges and a single vermilion accent that means one thing: a bus arriving now. Wallpaper (dynamic) colour stays available as a separate theme.

This document is the hand-off from the design canvas to the app. The `mockups/` folder holds the source boards (`*.dc.html`). They need the canvas runtime (`support.js`) to render, so treat them as reference markup: the inline styles carry exact sizes, colours and layout. The live canvas, which is private to the owner, is at https://claude.ai/artifact/2moQVzKavMsiReVM1LXeEc. The B row is the second row.

## Decisions already made

- **No hanko.** An earlier draft had a square 次 seal and a round 着 "Arrived" stamp. Both were removed at the owner's request. Don't reintroduce stamps or seals.
- **Arriving = vermilion "Now".** An 8 dp vermilion dot plus "Now" in Mincho 900, in the accent colour. This is the only use of the accent.
- **Route line is straight, with a brush texture.** It must pass exactly through the stop dots. Texture comes from rough edges, dry-brush gaps and a tapered tip where the travelled ink ends at the bus. The rest of the route is a pale ink wash.
- **Themes follow Kanade's model.** One structural style, many colourways, picked from a grouped gallery in Settings. The colourway names are traditional Japanese colours (伝統色).
- **The wordmark** is "Tsugi" in Zen Old Mincho 900 with a vermilion brush underline (only where a wordmark appears).
- **Sample data is always public.** Use Bugis Stn Exit A (01113), Bugis Stn Exit B (01059), Bugis Cube (01039) and Aft Bugis Stn Exit C (01119). Never use the owner's saved stops.

## Type

| Role | Font | Notes |
|---|---|---|
| Display: screen titles, stop names, minute numerals, "Now" | Zen Old Mincho 900 (600 for lighter use) | Google Fonts, OFL. Bundle a subset (see below). |
| Everything else | The app's current body font | Unchanged. Don't bundle Google Sans Flex. |

Minute numerals are large Mincho with a small sans "min" after them: 56 sp on the hero, 22–26 sp in rows.

Font budget: Zen Old Mincho's full Japanese set is several MB. Subset it with `pyftsubset` (fonttools) to Basic Latin, Latin-1 punctuation, `–·…`, and the kanji used in the UI: 藍 抹 茶 桜 藤 柿 墨 伝 統 色 夜 次 切 符. Target well under 100 KB per weight. Check the licence (OFL 1.1, no Reserved Font Name) and ship `OFL.txt` next to the font in `res/font/` or the docs.

## Colourways

Each colourway maps onto M3 `ColorScheme` roles as follows. Unlisted roles derive sensibly from these.

| Token | M3 role | 藍 Ai (default) | 抹茶 Matcha | 桜 Sakura | 藤 Fuji | 柿 Kaki |
|---|---|---|---|---|---|---|
| paper | background, surface | #F6F1E7 | #EFF0E3 | #F8EFEF | #F1EEF6 | #F8EFE4 |
| card | surfaceContainer (+Low/High tints) | #FFFDF8 | #FCFDF6 | #FFFBFA | #FCFBFE | #FFFAF3 |
| ink | onBackground, onSurface | #1E2433 | #1F2A22 | #2A2026 | #221F33 | #2B211B |
| muted | onSurfaceVariant | #5B6170 | #5A6558 | #6B5D63 | #625E74 | #6C5E52 |
| line | outlineVariant (1 dp card borders) | #E2DACB | #DCE0CC | #EDDCDD | #E0DAEA | #EADBC8 |
| badge | primary | #2F3E6E | #4E6B3A | #8E4A5E | #5B4E8C | #7A3B1E |
| onBadge | onPrimary | #FFFDF8 | #FCFDF6 | #FFFBFA | #FCFBFE | #FFFAF3 |
| chip | secondaryContainer | #E7EAF3 | #E3EBD8 | #F3E1E6 | #E6E1F2 | #F3E1D2 |
| chipInk | onSecondaryContainer | #2F3E6E | #3E5A2C | #7A3A4E | #4A3E7A | #6A3017 |
| now (accent) | tertiary | #C8402B | #B8452F | #C23B5A | #C8402B | #D2672A |
| nav | inverseSurface (floating toolbar) | #1E2433 | #1F2A22 | #2A2026 | #221F33 | #2B211B |
| navInk | inverseOnSurface | #C9CDD8 | #C7CFC2 | #D6C9CE | #CBC7D8 | #D8CCC0 |

**Night (墨 Sumi).** This is the dark scheme for Ai, from `B-Yoru.dc.html`:

| Token | Value |
|---|---|
| paper | #14171E |
| card | #1D222C |
| line | #2C3240 |
| ink | #ECE5D6 |
| muted | #9AA0AE |
| badge | #8C9BD6, with onBadge #14171E |
| chip | #262C3A, with chipInk #C3CBEB |
| now | #E0573F |
| nav (inverted) | #ECE5D6, with navInk #3A4152 |

The active nav pill uses the paper colour. Derive the dark schemes of the other colourways the same way: Sumi paper, card and line, plus a lightened version of that colourway's badge and accent. Every text and background pair must reach WCAG AA (4.5:1 for body, 3:1 for large Mincho numerals). Add a unit test that asserts this for every scheme.

The existing light / dark / system setting (`ThemeMode`, which uses per-app night mode) keeps working. A colourway supplies both of its schemes.

## Theme picker

Settings › Theme becomes a grouped gallery, modelled on kanade-bot's colourway picker.

- **伝統色 Traditional:** Ai, Matcha, Sakura, Fuji, Kaki. Each tile is a small phone-shaped preview of the "Next up" card in that colourway (paper, card, badge and "Now" accent), with the kanji and romaji name underneath. The selected tile gets a 2 dp ink outline and a check mark.
- **Dynamic:** Wallpaper. This is today's look: M3 Expressive, the current fonts, and no Ink & Paper components.

Store the choice in DataStore next to `theme` in `SettingsRepository`. The default for new installs is Ai. Existing installs keep Wallpaper, because the setting is unset; read the stored value to tell the two apart.

## Style switch

The colourway family decides the component style, so Wallpaper users see no change. Expose a `TsugiStyle` (`Ink` | `Expressive`) through a CompositionLocal from `TsugiTheme`. The components below branch on it. Screens keep their logic.

| Component | Ink & Paper treatment | Reference board |
|---|---|---|
| Typography | Display, headline and large title styles use Mincho 900. Minute numerals use Mincho. | all |
| Cards | 26 dp (hero) and 20–22 dp (rows) radius, card colour, 1 dp `line` border, no elevation or tonal shadow | B-Favourites, B-Stop |
| Hero "Next up" | "NEXT · 110 M AWAY" label in 12 sp with 2 sp tracking. Service number on the indigo cookie (the existing MaterialShapes cookie, `primary` fill) at 72 dp. Destination and stop. A hairline divider, then Mincho 56 sp minutes and "then 11 · 24 min" | B-Favourites |
| Service badge in rows | 48×40 dp, 14 dp radius, `chip` / `chipInk` (Favourites rows) or `primary` / `onPrimary` (Stop rows) | B-Favourites, B-Stop |
| Arriving | Vermilion dot plus "Now" in Mincho, in place of the countdown. No stamp, no "Arr" | B-Favourites, B-Stop |
| Stop time tiles | Three tiles per service, `paper` background, Mincho minutes | B-Stop |
| Stop header | Mincho 34 sp name, a 48×3 dp vermilion underline, then "road · code · nearest MRT · across the road" | B-Stop |
| Floating toolbar | `inverseSurface` pill, 30 dp radius. The active slot is a `paper` pill with `ink` text. Keep the fixed-width sliding highlight that already exists | B-Favourites |
| Service route | A straight brush line through the stop dots. Travelled part is solid ink ending in a tapered tip at the bus, then a pale wash. Passed stops at 40% opacity. The bus is an indigo disc with a bus glyph and Mincho minutes. Your stop is a 22 dp vermilion dot with a "You board here" outlined chip | B-Route |
| Night | Same layout on Sumi. Ended services at 45% opacity with "Service ended for today" | B-Yoru |

**Brush texture in Compose.** Prefer a small bundled vector or 9-patch-style brush texture tiled or stretched along the line. `RenderEffect` noise is unavailable before API 33 and must not be required. A drawn path with slightly irregular edges is an acceptable fallback. Keep the line centre exactly on the dot centres.

### Phase 2 (only after phase 1 is solid)

- **切符 ticket widget (Glance):** a stub on the left with the indigo cookie and stop code, a dashed perforation with half-circle notches, and the stop name plus three rows on the right. The arriving row shows "Now". See B-Ticket.
- **Live Update / tracking notification:** a progress bar with a dot per stop and the bus as an indigo disc. Arrival alert with a vermilion cookie and the service number. Platform limits apply, so check `Notification.ProgressStyle` points and segments (API 36) against what `TrackingNotifications` already does. See B-Ticket.

## Project context you need

**Repo and stack**
- Repo: `hoshinoht/tsugi` (private). Default branch `master`, currently v0.3.0. Package `dev.cantabile.tsugi`.
- Stack: Kotlin 2.4.20, AGP 9.4.1 (built-in Kotlin), Gradle 9.8.0.
- SDK levels: compileSdk 37.1, targetSdk 37, minSdk 31.
- Compose BOM 2026.09.00 with material3 pinned to 1.5.0-alpha29 for the M3 Expressive APIs, plus Glance 1.2.0 with WorkManager pinned to 2.12.0 (Android 17 crash fix).

**Where things are**
- Theme: `ui/theme/Theme.kt` (dynamic colour only today).
- Settings: `data/SettingsRepository.kt` (DataStore, `ThemeMode` enum) and `ui/SettingsScreen.kt`.
- Screens: `FavouritesScreen`, `StopScreen`, `NearbyScreen`, `ServiceScreen`, `StationScreen`, `SearchScreen`, `PlaceScreen`.
- Shared components: `ui/Components.kt`.
- Navigation and toolbar: `ui/TsugiRoot.kt`.

**Build and checks**
- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`, as in `.github/workflows/ci.yml`.
- Builds work without an LTA key, which is read from env → `.env` → `local.properties` and is never committed.

**Rules from the owner**
- Don't change build configuration (`build.gradle.kts`, `libs.versions.toml`, `gradle.properties`, settings, the wrapper) or add dependencies without asking. If something truly needs it, stop and explain in the PR rather than doing it. Fonts go in `res/font`, which needs no Gradle change.
- Keep the app lightweight. Note the size change caused by fonts in the PR.
- No private data in docs or screenshots. Public places only.
- House style for prose: plain, specific, no marketing tone. Update `CHANGELOG.md` (Unreleased) and the README's feature list.
- Write code that reads like the surrounding code: match its comment density, naming and idiom.

**Open items elsewhere (don't fix here)**
- Predictive back still shows One UI's arrow on the owner's Galaxy S25+, even with `enableOnBackInvokedCallback`.
- Local `dev` is behind `master`.
