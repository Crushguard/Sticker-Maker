# Love Stickers · every screen, mapped

Screenshots of the real app on an Android emulator, next to the design frame each one implements (`design/Screens.dc.html`: 13 screens, 42 frames in flow order). The on-device tour (`app/src/androidTest/.../tour/ScreenTourTest.kt`) drives the app like a user and checks each state before it takes the screenshot.

- **Result:** 42 of 42 design frames captured, 5 extra states, 19 languages, 0 failed steps · tour passed
- **Source:** `claude/blissful-brahmagupta-fd53k4` @ `afff3a1`
- **Captured by:** [CI run](https://github.com/Crushguard/Sticker-Maker/actions/runs/36104918419) · 2026-09-25 07:06 UTC
- **Device:** sdk_gphone64_x86_64, Android API 34, 1080×2138 px at 440 dpi
- **Backend:** Firestore and Storage emulators seeded by `scripts/upload-pack.js` (14 packs, 8 themes); the debug build points at them with `-PfirebaseEmulatorHost=10.0.2.2`
- **WhatsApp:** a test double (`testing/whatsapp-stub`) that reads each pack back through the app's ContentProvider and checks WhatsApp's pack rules before answering
- **Test log:** [`app/instrument.txt`](app/instrument.txt) · device warnings and errors: [`app/logcat-warnings.txt`](app/logcat-warnings.txt)

Design frames are rendered from `design/Prototype.dc.html`. The prototype's sample photos and own-pack stickers were never exported with the design, so frames 18–24 and 26–28 show empty tiles on the design side; their layout is still the reference.

## Navigation map

```mermaid
flowchart TD
  splash["Launch · 01"] -->|first run| onboarding["Onboarding · 02 03"]
  splash -->|returning| home
  onboarding --> customize["Pick your themes · 04"]
  customize --> home["Home · 06 07 08 17 29 · offline 39"]
  home -->|card| detail["Pack page · 09 10 11 · add states 12–16 · no WhatsApp 40"]
  home -->|Create| create["Import · 18"]
  home <-->|tab| mypacks["My Packs · 24 · menu 26 · confirms 27 28 · empty 41"]
  home -->|gear| settings["Settings · 30 · rate 31–34 · alerts 37 · clear 38"]
  create --> editor["Cut out · 19 20"]
  editor --> details["Pack details · 21"]
  details -->|exported| mypacks
  mypacks -->|heart| saved["Saved · 25 · empty 42"]
  mypacks -->|card| owndetail["Own pack page · 22 23"]
  mypacks -->|Create| create
  mypacks -->|gear| settings
  saved -->|card| detail
  settings --> edit["Edit themes · 05"]
  settings --> language["Language · 36"]
  settings --> contact["Contact us · 35"]
  detail -.->|ENABLE_STICKER_PACK| whatsapp[("WhatsApp")]
  details -.->|ENABLE_STICKER_PACK| whatsapp
```

## 00 Launch

| Frame | Design | App | Route · what the tour verified |
|---|---|---|---|
| ✅ **01** Loading screen | <img src="design/01-launch-loading-screen.png" width="210"> | <img src="app/01-launch.png" width="210"> | `splash` · Branding beat before onboarding (first run). |

## 01 Onboarding

| Frame | Design | App | Route · what the tour verified |
|---|---|---|---|
| ✅ **02** Slide 1 · Curated packs, one tap to WhatsApp | <img src="design/02-onboarding-slide-1-curated-packs-one-tap-to-whatsapp.png" width="210"> | <img src="app/02-onboarding-slide-1.png" width="210"> | `onboarding` · Slide 1 with Skip; Next advances. |
| ✅ **03** Slide 2 · Make your own, animated too | <img src="design/03-onboarding-slide-2-make-your-own-animated-too.png" width="210"> | <img src="app/03-onboarding-slide-2.png" width="210"> | `onboarding` · Slide 2; Skip hides, CTA reads Get started. |

## 02 Customization

| Frame | Design | App | Route · what the tour verified |
|---|---|---|---|
| ✅ **04** First run | <img src="design/04-customization-first-run.png" width="210"> | <img src="app/04-customize-first-run.png" width="210"> | `customize` · Offline first run: the eight themes come from the built-in fallback list. |
| ✅ **05** From Settings · Edit themes | <img src="design/05-customization-from-settings-edit-themes.png" width="210"> | <img src="app/05-customize-edit-themes.png" width="210"> | `customizeEdit` · Settings › Edit themes, pre-checked from prefs; six themes picked, CTA reads Save. |

## 03 Home

| Frame | Design | App | Route · what the tour verified |
|---|---|---|---|
| ✅ **06** Home · Trending | <img src="design/06-home-home-trending.png" width="210"> | <img src="app/06-home-trending.png" width="210"> | `home` · Six themes picked; two packs added, two hearted (♥ Saved chip shows). |
| ✅ **07** Home · Animated | <img src="design/07-home-home-animated.png" width="210"> | <img src="app/07-home-animated.png" width="210"> | `home` · Animated chip: only animated packs, ANIMATED badges, static first-frame previews. |
| ✅ **08** Home › Search | <img src="design/08-home-home-search.png" width="210"> | <img src="app/08-home-search.png" width="210"> | `home` · Search replaces the bar and chips; "night" matches titles and the Good night theme. |

## 04 Stickers

| Frame | Design | App | Route · what the tour verified |
|---|---|---|---|
| ✅ **09** Clingy Mango · 18 stickers | <img src="design/09-stickers-clingy-mango-18-stickers.png" width="210"> | <img src="app/09-detail-clingy-mango.png" width="210"> | `detail/clingy-mango` · Added pack: grid of 18, bar reads Added to WhatsApp. |
| ✅ **10** Mango Moves · animated | <img src="design/10-stickers-mango-moves-animated.png" width="210"> | <img src="app/10-detail-mango-moves.png" width="210"> | `detail/mango-moves` · Animated pack with the ANIMATED badge; added. |
| ✅ **11** Big Words · 18 stickers | <img src="design/11-stickers-big-words-18-stickers.png" width="210"> | <img src="app/11-detail-big-words.png" width="210"> | `detail/big-words` · Hearted pack (rose heart), not added yet. |

## 05 Add states

| Frame | Design | App | Route · what the tour verified |
|---|---|---|---|
| ✅ **12** 1 · Idle | <img src="design/12-add-states-1-idle.png" width="210"> | <img src="app/12-add-1-idle.png" width="210"> | `detail/sorry-love` · Idle bar with its hint; 18 stickers stream from the Storage emulator. |
| ✅ **13** 2 · Downloading | <img src="design/13-add-states-2-downloading.png" width="210"> | <img src="app/13-add-2-downloading.png" width="210"> | `detail/sorry-love` · Retry after the file was restored; the debug build paces the download (0.6 s per file) to hold the state. |
| ✅ **14** 3 · Sent to WhatsApp | <img src="design/14-add-states-3-sent-to-whatsapp.png" width="210"> | <img src="app/14-add-3-sent.png" width="210"> | `detail/sorry-love` · Bar reads Sent to WhatsApp… under WhatsApp's confirm (test double). Provider check: ✓ metadata · “Sorry, My Love” by PIP Technologies ✓ 18 stickers · 1 to 3 emojis each ✓ tray · tray.png 96×96 PNG, 4.6 KB ✓ stickers · 512×512 WebP, largest 36.6 KB of 100 KB ✓ static pack · no animated files |
| ✅ **15** 4 · Added | <img src="design/15-add-states-4-added.png" width="210"> | <img src="app/15-add-4-added.png" width="210"> | `detail/sorry-love` · WhatsApp answered RESULT_OK and its whitelist provider reports the pack. |
| ✅ **16** Retry · download failed | <img src="design/16-add-states-retry-download-failed.png" width="210"> | <img src="app/16-add-retry-failed.png" width="210"> | `detail/sorry-love` · Real failure: packs/sorry-love/07.webp was deleted from the Storage emulator mid-catalog, so getFile() 404s. |
| ✅ **17** Card pill · same states, small | <img src="design/17-add-states-card-pill-same-states-small.png" width="210"> | <img src="app/17-add-card-pill.png" width="210"> | `home` · Card pills in miniature: Clingy Mango added, Mango Moves downloading (paced by the debug build). |

## 06 Create

| Frame | Design | App | Route · what the tour verified |
|---|---|---|---|
| ✅ **18** Import | <img src="design/18-create-import.png" width="210"> | <img src="app/18-create-import.png" width="210"> | `create` · Four photos from MediaStore through the (stubbed) system photo picker. |
| ✅ **19** Cut out · auto result | <img src="design/19-create-cut-out-auto-result.png" width="210"> | <img src="app/19-create-cutout-auto.png" width="210"> | `editor` · ML Kit selfie segmentation ran on the phone for every sticker; white outline on. |
| ✅ **20** Cut out · fixing edges | <img src="design/20-create-cut-out-fixing-edges.png" width="210"> | <img src="app/20-create-cutout-brush.png" width="210"> | `editor` · Brush tool with two dabs restoring edges; brush size row shows. |
| ✅ **21** Pack details | <img src="design/21-create-pack-details.png" width="210"> | <img src="app/21-create-pack-details.png" width="210"> | `packDetails` · Tray from the first sticker, name typed, export facts card. |
| ✅ **22** Made by you · Us, always | <img src="design/22-create-made-by-you-us-always.png" width="210"> | <img src="app/22-own-us-always.png" width="210"> | `detail/own` · Own pack page from Room + filesDir: Made by you, added. |
| ✅ **23** Made by you · Just Us | <img src="design/23-create-made-by-you-just-us.png" width="210"> | <img src="app/23-own-just-us.png" width="210"> | `detail/own` · Saved to My Packs only: never sent to WhatsApp, so the bar offers Add. |

## 07 My Packs and Saved

| Frame | Design | App | Route · what the tour verified |
|---|---|---|---|
| ✅ **24** My Packs | <img src="design/24-my-packs-and-saved-my-packs.png" width="210"> | <img src="app/24-my-packs.png" width="210"> | `myPacks` · In WhatsApp (2) and Made by you (2), removal info card, tab bar with My Packs selected. |
| ✅ **25** My Packs › Saved | <img src="design/25-my-packs-and-saved-my-packs-saved.png" width="210"> | <img src="app/25-saved.png" width="210"> | `saved` · Hearted packs, reached from the My Packs heart. |
| ✅ **26** Card menu | <img src="design/26-my-packs-and-saved-card-menu.png" width="210"> | <img src="app/26-my-packs-card-menu.png" width="210"> | `myPacks` · ⋯ menu for an added catalog pack: re-add or remove the local copy. |
| ✅ **27** Confirm · remove from app | <img src="design/27-my-packs-and-saved-confirm-remove-from-app.png" width="210"> | <img src="app/27-my-packs-confirm-remove.png" width="210"> | `myPacks` · Destructive confirm; Keep cancels. |
| ✅ **28** Confirm · delete your pack | <img src="design/28-my-packs-and-saved-confirm-delete-your-pack.png" width="210"> | <img src="app/28-my-packs-confirm-delete.png" width="210"> | `myPacks` · Deleting your own pack; Keep cancels. |
| ✅ **29** Home · ♥ Saved | <img src="design/29-my-packs-and-saved-home-saved.png" width="210"> | <img src="app/29-home-saved-chip.png" width="210"> | `home` · ♥ Saved chip filters Home to the hearted packs. |

## 08 Settings

| Frame | Design | App | Route · what the tour verified |
|---|---|---|---|
| ✅ **30** Settings | <img src="design/30-settings-settings.png" width="210"> | <img src="app/30-settings.png" width="210"> | `settings` · Alerts hero, preferences with values, about rows, free-forever card. |
| ✅ **31** Rate · pick a star | <img src="design/31-settings-rate-pick-a-star.png" width="210"> | <img src="app/31-rate-stars.png" width="210"> | `settings` · Rate sheet, no star picked. |
| ✅ **32** Rate · 5 stars → Google Play | <img src="design/32-settings-rate-5-stars-google-play.png" width="210"> | <img src="app/32-rate-store.png" width="210"> | `settings` · Five stars lead to the Google Play ask. |
| ✅ **33** Rate · 1 to 4 stars → feedback | <img src="design/33-settings-rate-1-to-4-stars-feedback.png" width="210"> | <img src="app/33-rate-feedback.png" width="210"> | `settings` · One to four stars lead to the feedback note. |
| ✅ **34** Rate · thanks | <img src="design/34-settings-rate-thanks.png" width="210"> | <img src="app/34-rate-thanks.png" width="210"> | `settings` · Sent through the mail composer (stubbed: the emulator has no mail app). |
| ✅ **35** Contact us | <img src="design/35-settings-contact-us.png" width="210"> | <img src="app/35-contact.png" width="210"> | `contact` · Contact form: message, optional email, attach note. |
| ✅ **36** Language | <img src="design/36-settings-language.png" width="210"> | <img src="app/36-language.png" width="210"> | `language` · System default selected; RTL badges on Arabic and Urdu. |
| ✅ **37** New pack alerts · permission | <img src="design/37-settings-new-pack-alerts-permission.png" width="210"> | <img src="app/37-alerts-permission.png" width="210"> | `settings` · Turning alerts back on without the Android 13+ notification permission asks first. |
| ✅ **38** Confirm · clear downloads | <img src="design/38-settings-confirm-clear-downloads.png" width="210"> | <img src="app/38-settings-confirm-clear.png" width="210"> | `settings` · Confirm with the real size of the downloaded packs. |

## 09 Errors and empty states

| Frame | Design | App | Route · what the tour verified |
|---|---|---|---|
| ✅ **39** No internet | <img src="design/39-errors-and-empty-states-no-internet.png" width="210"> | <img src="app/39-home-offline.png" width="210"> | `home` · Airplane mode on a fresh install: nothing cached, full-screen offline state with Retry. |
| ✅ **40** WhatsApp not installed | <img src="design/40-errors-and-empty-states-whatsapp-not-installed.png" width="210"> | <img src="app/40-no-whatsapp.png" width="210"> | `detail/flirty-shy` · No com.whatsapp on the device: tapping Add raises the install sheet before any download. |
| ✅ **41** My Packs · empty | <img src="design/41-errors-and-empty-states-my-packs-empty.png" width="210"> | <img src="app/41-my-packs-empty.png" width="210"> | `myPacks` · After removing the only added pack for real; its files are gone from filesDir. |
| ✅ **42** Saved · empty | <img src="design/42-errors-and-empty-states-saved-empty.png" width="210"> | <img src="app/42-saved-empty.png" width="210"> | `saved` · Nothing hearted yet. |

## Extra states

States the design shows only in passing, or checks worth keeping a picture of.

| Key | App | Route · what the tour verified |
|---|---|---|
| **x01** Home after Retry (first-run themes) | <img src="app/x01-home-after-retry-first-run-themes.png" width="210"> | `home` · Back online, Retry reloads the catalog from the Firestore emulator; only Couples/Cute/Funny packs show. |
| **x02** Cut out · caption | <img src="app/x02-cut-out-caption.png" width="210"> | `editor` · Text tool: the design's "miss u" caption on the first sticker. |
| **x03** Own pack in WhatsApp's confirm | <img src="app/x03-own-pack-in-whatsapp-s-confirm.png" width="210"> | `packDetails` · Exported on the phone (512×512 WebP, 96×96 tray) and read back through the provider: ✓ metadata · “Us, always” by Made by you ✓ 4 stickers · 1 to 3 emojis each ✓ tray · tray.png 96×96 PNG, 16.3 KB ✓ stickers · 512×512 WebP, largest 54.7 KB of 100 KB ✓ static pack · no animated files |
| **x04** Own pack added from its page | <img src="app/x04-own-pack-added-from-its-page.png" width="210"> | `detail/own` · Local packs skip the download and hand straight to WhatsApp; provider check: ✓ metadata · “Just Us” by Made by you ✓ 3 stickers · 1 to 3 emojis each ✓ tray · tray.png 96×96 PNG, 16.3 KB ✓ stickers · 512×512 WebP, largest 53.1 KB of 100 KB ✓ static pack · no animated files |
| **x05** My Packs after deleting Just Us | <img src="app/x05-my-packs-after-deleting-just-us.png" width="210"> | `myPacks` · Delete removed the Room rows and the pack directory; Us, always stays. |

## Every language

The same screens in each language the app ships (`LocaleTourTest`), switched through Android's per-app language like the Language screen does. Right-to-left languages mirror the layout. Half-size captures.

| Language | Home | Pack page | My Packs | Settings |
|---|---|---|---|---|
| **English** `en` | <img src="app/lang-en-home.png" width="150"> | <img src="app/lang-en-pack.png" width="150"> | <img src="app/lang-en-my-packs.png" width="150"> | <img src="app/lang-en-settings.png" width="150"> |
| **العربية** `ar` | <img src="app/lang-ar-home.png" width="150"> | <img src="app/lang-ar-pack.png" width="150"> | <img src="app/lang-ar-my-packs.png" width="150"> | <img src="app/lang-ar-settings.png" width="150"> |
| **Español** `es` | <img src="app/lang-es-home.png" width="150"> | <img src="app/lang-es-pack.png" width="150"> | <img src="app/lang-es-my-packs.png" width="150"> | <img src="app/lang-es-settings.png" width="150"> |
| **Português** `pt` | <img src="app/lang-pt-home.png" width="150"> | <img src="app/lang-pt-pack.png" width="150"> | <img src="app/lang-pt-my-packs.png" width="150"> | <img src="app/lang-pt-settings.png" width="150"> |
| **Português (Brasil)** `pt-BR` | <img src="app/lang-pt-BR-home.png" width="150"> | <img src="app/lang-pt-BR-pack.png" width="150"> | <img src="app/lang-pt-BR-my-packs.png" width="150"> | <img src="app/lang-pt-BR-settings.png" width="150"> |
| **Français** `fr` | <img src="app/lang-fr-home.png" width="150"> | <img src="app/lang-fr-pack.png" width="150"> | <img src="app/lang-fr-my-packs.png" width="150"> | <img src="app/lang-fr-settings.png" width="150"> |
| **हिन्दी** `hi` | <img src="app/lang-hi-home.png" width="150"> | <img src="app/lang-hi-pack.png" width="150"> | <img src="app/lang-hi-my-packs.png" width="150"> | <img src="app/lang-hi-settings.png" width="150"> |
| **Bahasa Indonesia** `id` | <img src="app/lang-id-home.png" width="150"> | <img src="app/lang-id-pack.png" width="150"> | <img src="app/lang-id-my-packs.png" width="150"> | <img src="app/lang-id-settings.png" width="150"> |
| **Türkçe** `tr` | <img src="app/lang-tr-home.png" width="150"> | <img src="app/lang-tr-pack.png" width="150"> | <img src="app/lang-tr-my-packs.png" width="150"> | <img src="app/lang-tr-settings.png" width="150"> |
| **اردو** `ur` | <img src="app/lang-ur-home.png" width="150"> | <img src="app/lang-ur-pack.png" width="150"> | <img src="app/lang-ur-my-packs.png" width="150"> | <img src="app/lang-ur-settings.png" width="150"> |
| **Deutsch** `de` | <img src="app/lang-de-home.png" width="150"> | <img src="app/lang-de-pack.png" width="150"> | <img src="app/lang-de-my-packs.png" width="150"> | <img src="app/lang-de-settings.png" width="150"> |
| **Русский** `ru` | <img src="app/lang-ru-home.png" width="150"> | <img src="app/lang-ru-pack.png" width="150"> | <img src="app/lang-ru-my-packs.png" width="150"> | <img src="app/lang-ru-settings.png" width="150"> |
| **Italiano** `it` | <img src="app/lang-it-home.png" width="150"> | <img src="app/lang-it-pack.png" width="150"> | <img src="app/lang-it-my-packs.png" width="150"> | <img src="app/lang-it-settings.png" width="150"> |
| **简体中文** `zh` | <img src="app/lang-zh-home.png" width="150"> | <img src="app/lang-zh-pack.png" width="150"> | <img src="app/lang-zh-my-packs.png" width="150"> | <img src="app/lang-zh-settings.png" width="150"> |
| **فارسی** `fa` | <img src="app/lang-fa-home.png" width="150"> | <img src="app/lang-fa-pack.png" width="150"> | <img src="app/lang-fa-my-packs.png" width="150"> | <img src="app/lang-fa-settings.png" width="150"> |
| **עברית** `he` | <img src="app/lang-he-home.png" width="150"> | <img src="app/lang-he-pack.png" width="150"> | <img src="app/lang-he-my-packs.png" width="150"> | <img src="app/lang-he-settings.png" width="150"> |
| **پښتو** `ps` | <img src="app/lang-ps-home.png" width="150"> | <img src="app/lang-ps-pack.png" width="150"> | <img src="app/lang-ps-my-packs.png" width="150"> | <img src="app/lang-ps-settings.png" width="150"> |
| **Hausa** `ha` | <img src="app/lang-ha-home.png" width="150"> | <img src="app/lang-ha-pack.png" width="150"> | <img src="app/lang-ha-my-packs.png" width="150"> | <img src="app/lang-ha-settings.png" width="150"> |
| **မြန်မာ** `my` | <img src="app/lang-my-home.png" width="150"> | <img src="app/lang-my-pack.png" width="150"> | <img src="app/lang-my-my-packs.png" width="150"> | <img src="app/lang-my-settings.png" width="150"> |
