# Create › Cut out: Add, Draw and Animate (design → code map)

Implements Claude Design's "Decorate and Animate" page for the Create editor. Reference frame 390 × 844, where 1 CSS px = 1 dp and font px = sp.

## Sources

The design project is "Sticker Maker" on claude.ai/design. Copies are in the implementer's scratchpad under `design-sync/`, never in this public repo. When sources conflict, trust them in this order:

1. `Prototype.dc.html`: the editor markup at L301-396; the logic at L718-770 (`textCss`, `layerBox`, `outlineFilter`, `groupEl`, `applyDemo`, `addLayer`, `flushStrokes`) and L1029-1072 (`useTool`, `tapCanvas`, `undoEdit`); the `ed` view-model at L1274-1331. The file was cut at 256 KB inside `renderVals`, after the editor; nothing the editor uses is missing.
2. `Decorate and Animate.dc.html`: decisions, measurements, motion timings and the copy table. It is the source of every number marked "handoff" below.
3. `Screens.dc.html` §"Create decorate and animate": 14 frames with captions (listed in §12).
4. Data files: `assets/decor/decor.json`, `assets/emoji/subset.json`, `assets/motion/presets.json`, `assets/text-styles.json`, `assets/text/quick-phrases.json`.

Choices made where the design is silent or conflicts with WhatsApp are marked ⚠ and collected in §13.

---

## 1. Scope

- **Tool bar:** Auto · Brush · Erase · **Add** · **Draw** · **Animate**. Add replaces Text. Zoom leaves the bar and becomes a button on the canvas card.
- **Layers:** text, emoji, decoration pieces and drawings.
  - Up to 8 per sticker.
  - Each can be selected, moved, resized, rotated, flipped, duplicated, put behind the cut-out, deleted or edited (text only).
- **Add sheet:** Text (30 characters, 4 styles, 8 colours, 3 font moods, quick phrases), Emoji (327 bundled Fluent 3D emoji, skin tones on long-press, Recent), Stickers (54 decoration pieces).
- **Draw:** a marker with an ink edge, 8 colours and 3 sizes. Leaving Draw turns the strokes into one layer.
- **Animate:** 9 whole-sticker presets from `presets.json`, played live. The export renders them on the device.
- **Outline:** on/off, Thin/Medium/Thick and 5 colours. It wraps the subject and every layer as one die-cut shape, and applies to animated stickers too.
- **Rail:** decorated thumbnails and an ANIM badge.
- **Undo:** one stack per sticker covering every change.
- **Pack details:** "under 500 KB each" for animated packs.
- **Emoji tags:** each sticker's WhatsApp tags come from its layers.
- **Settings › Licences:** a new row and screen.

Not in v1 (handoff): per-layer motion, custom fonts, text on a path, GIF layers.

## 2. Assets and data shipped in the app

| App path | From | Notes |
|---|---|---|
| `assets/decor/{doodles-1..30,props-1..24}.webp` | design `assets/decor/` | Trimmed RGBA, longest side ≤ 512 px, ≤ 16 KB each. |
| `assets/decor/decor.json` | design, verbatim | `pieces[]` holds `file`, `set` (doodles or props), `label` (English, used for accessibility), `anchor` (`center` or `head-top`), `defaultWidth` (fraction of 512) and `emojis[]`. |
| `assets/emoji/{slug}.webp` | design `assets/emoji/{slug}.png` (Fluent 3D, 256 px) | Converted to lossy WebP (q 86, alpha 100) to keep the APK small. 327 files. |
| `assets/emoji/subset.json` | design, with every `file` rewritten `.png` → `.webp` | `tabs[]` (love, smileys, hearts, hands, animals, food, symbols), each with `items[]` holding `name`, `cp`, `glyph`, `file` and optional `skinTones`. |
| `assets/motion/presets.json` | design, verbatim | 9 presets. See §7. |
| `assets/text/text-styles.json` | design `assets/text-styles.json`, verbatim | Styles, colours, font moods per script, outline thickness and colours, marker. |
| `assets/text/quick-phrases.json` | design, plus 15 locales we translate | `{lang: [14 phrases]}`. Resolve the app language, then `en`. `pt-rBR` falls back to `pt`, then `en`. |
| `res/font/lettering_{mirza,kalam,amaticsc,lilitaone,ruslandisplay,lalezar,yatraone,karantina}.ttf` | github.com/google/fonts (OFL) | Static fonts. |
| `assets/fonts/lettering_caveat.ttf` | `Caveat[wght].ttf` (OFL, variable) | Loaded with `Typeface.Builder(assets, path).setFontVariationSettings("'wght' 700")` on API 26+; on API 24-25, the default instance plus fake bold. |
| `assets/licenses/OFL-{Caveat,Mirza,Kalam,AmaticSC,LilitaOne,RuslanDisplay,Lalezar,YatraOne,Karantina}.txt`, `MIT-FluentEmoji.txt`, `ISC-Lucide.txt` | upstream licence texts | Shipped next to the existing `OFL-*.txt`. |

**Skin tones.** The 42 Hands items with `skinTones: true` bundle only their Default tone. The five other tones download on demand from `https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/{Name}/{Tone}/3D/{slug}_3d_{tone}.png`:
- `Name` is the subset `name`; every one of the 327 matches a Fluent folder exactly (checked 2026-09-30).
- `Tone` ∈ Light, Medium-Light, Medium, Medium-Dark, Dark.
- `slug` is `name` lower-cased with spaces replaced by `_`.
- `tone` is `Tone` lower-cased.
- Downloads are cached in `filesDir/emoji-tones/`.

## 3. Layer model (512 canvas space)

A layer has:
- an id;
- content: one of
  - Text: text, style, colour, font mood;
  - Emoji: file, glyph, skin tone;
  - Decor: file, emojis;
  - Drawing: marker strokes, each with its own points, colour and size;
- a centre `(cx, cy)` in px;
- `scale`, `rotation` (degrees, clockwise), `flipped` and `behind`.

**Base sizes** (a layer's rendered size is its base size × `scale`):
- Text: font size 52 px.
- Emoji: width 0.30 × 512.
- Decor: `defaultWidth` × 512.
- Drawing: the stroke bounding box as drawn.

**Placement of a new layer.** It is always selected on arrival.
- Default: at the canvas centre.
- The first text layer of a sticker: bottom centre, `(50%, 87%)`, where today's caption sits.
- Decor with `anchor: head-top`: horizontally at the cut-out's bounding-box centre, with its centre a quarter of its own height above the box top. The box comes from the mask alpha > 0.5.
- A Drawing: the centre of its strokes.

**Limits:**
- ≤ 8 layers per sticker. The 9th shows the toast `create_toast_layer_limit` with the value 8.
- Rendered width stays between 10% and 100% of the canvas; emoji are capped at 50% because the art is 256 px.
- The centre is clamped so that at least a quarter of the layer's box stays inside the canvas.

**Snapping (handoff):**
- During a drag, a centre within 6 px of the canvas centre line (x = 256 or y = 256) snaps to it. A 1.5 dp dashed Rose guide shows on that axis, with a light haptic tick on entering the snap.
- Rotation snaps to 0° within ±4°.

**Actions:**
- **Duplicate:** a copy with a new id, offset +8% of the canvas on x and y (clamped), selected. It counts toward the 8.
- **Flip:** toggles `flipped` (mirror on x in the layer's own frame).
- **Behind / In front:** toggles `behind`. Behind layers draw under the cut-out.
- **Delete:** removes the layer and clears the selection.
- **Edit** (text only): opens Add › Text on that layer.

**Emoji tags** for a sticker (1-3):
1. The glyphs of its emoji layers, in layer order.
2. Then the `emojis` of its decor layers.
3. Deduplicated and cut at 3.
4. If none, the pack default (`DEFAULT_EMOJIS`).

## 4. Undo (one stack per sticker)

- Every user-visible change is one step:
  - a Brush or Erase stroke;
  - one Draw stroke;
  - adding, deleting, duplicating, flipping or moving a layer to Behind / In front;
  - one whole gesture (drag, pinch, twist, or a handle drag), recorded at gesture start;
  - one text-editing session: every keystroke, phrase and style, colour or font change applied to the same text layer while the sheet stays on that layer;
  - an outline switch, thickness or colour change;
  - a preset change.
- Draw strokes are undone one by one while Draw is active. When Draw is left, its strokes collapse into a single step whose undo removes the whole drawing layer.
- The header Undo is enabled (Ink) while the stack is non-empty, and disabled (#B4BAC4) otherwise. An empty stack shows the toast `create_toast_nothing_to_undo`, as today.

## 5. Rendering (shared by the live canvas and the export)

Draw order inside one sticker (512 space):
1. **Outline pass** (if on), in the outline colour, as one union shape:
   - the subject silhouette;
   - each layer's silhouette under its transform;
   - live Draw strokes.
   Thickness `r` is 5 / 8 / 12 px (Thin / Medium / Thick, handoff) as seen in the exported file. Because the export fits 512 into 460, the working radius is `r × 512 / 460`; Medium ≈ 9 px, today's value.
2. Behind layers.
3. Subject: source masked by the cut-out.
4. Front layers.
5. Live Draw strokes.

**Outline.** Dilating a union is the union of dilations, so each element gets its own silhouette:
- Subject: a 512 bitmap, recomputed when the mask or the thickness changes. It extends today's `StickerRenderer.outlineOf` with a radius parameter.
- Image layers (emoji, decor): the source bitmap's alpha dilated by `r / renderScale`, padded by that radius, and cached per (file, thickness, scale bucket).
- Text: the glyph path stroked at `2r` (plus the style's own stroke).
- Drawing: the same paths stroked at `width + 2 × edge + 2r`.

The outline colour is applied by tinting (SRC_IN). An outline change crossfades over 160 ms on the live canvas.

**Text styles** (`text-styles.json`; sizes in px at 512 for the 52 px default):
- **Classic:**
  - Font: the UI font (Hanken Grotesk ExtraBold); other scripts fall back per glyph to the system font.
  - Fill: the chosen colour.
  - Shadow: `dy 3` in #1E2128, no blur, plus a 12 px glow of rgba(0,0,0,.35).
- **Sticker:**
  - Font: the lettering font for the mood and script.
  - Fill: the chosen colour, over a 0.16 em white stroke (stroke first).
  - Shadow: `dy 3`, blur 3, rgba(0,0,0,.18).
- **Bold stroke:**
  - Font: lettering.
  - White fill over a 0.2 em stroke in the chosen colour.
- **Bubble:**
  - Font: lettering.
  - Text: #1E2128 at 0.78 × size.
  - Bubble: white fill, 5 px #1E2128 border, radius 28, padding 12 × 22, all scaled by size / 52.
  - Tail: 26 × 20 at the bottom-start, mirrored for RTL text.
- **Fit:** at most 30 characters and 2 lines. A line wider than 480 px (at the current size) splits at the best space; if it's still too wide, the render shrinks to fit.
- **Font per string, never per UI language.** The script rules are from Custom Stickers `LetteringFonts`:
  - Arabic script → the Arabic font of the mood;
  - Hebrew → Hebrew;
  - Cyrillic → Cyrillic;
  - Devanagari → Devanagari;
  - otherwise Latin.

| Mood | Latin | Cyrillic | Arabic | Devanagari | Hebrew |
|---|---|---|---|---|---|
| Rounded (default) | Baloo 2 800 | Rubik 800 | Baloo Bhaijaan 2 800 | Baloo 2 800 | Rubik 800 |
| Hand | Caveat 700 | Caveat 700 | Mirza 700 | Kalam 700 | Amatic SC 700 |
| Display | Lilita One 400 | Ruslan Display 400 | Lalezar 400 | Yatra One 400 | Karantina 700 |

**Marker** (Draw):
- Widths: S 8, M 14, L 22 px.
- Each stroke first draws an ink edge of #1E2128, 3 px wider on each side; round cap and join.
- Points are smoothed with quadratic segments.
- Colours: the 8 text colours. Default Rose, size M.

**Export frame.** The composite above, fitted into 460 of 512 as today (`renderExportCanvas`).

## 6. Live canvas

- The canvas draws the scene on every frame. The cut-out mask, the silhouettes and the rendered text are cached, so a frame is a handful of bitmap draws and gestures stay 1:1 with the finger (handoff: "Drag has no lag").
- While Brush or Erase is active, layers draw at 40% opacity (a 120 ms fade) and ignore touches.
- Zoom (1.6×) and pan wrap the whole scene. When zoomed, pinch and pan move the view, and a one-finger drag on a layer still moves it.
- Animation: a Compose frame clock drives the preset transform and particles.
  - Picking a preset restarts the loop at t = 0.
  - Reduced motion: the canvas stays still until tapped, then plays exactly one loop.

## 7. Motion presets (`presets.json`)

Each preset has `durationMs`, `frames` (12 or 16 at 12 fps), `baseScale`, `pivot{x,y}` (canvas fractions) and `keyframes[]` with `t` (0-1), `scaleX`, `scaleY`, `rotate` (degrees), `dx`, `dy` (canvas fractions) and `easing`.

**Evaluation at time t:**
- Find the keyframe pair that brackets t.
- Ease with the earlier keyframe's easing. `linear`; `ease-in-out` = cubic-bezier(.42,0,.58,1); `ease-out` = (0,0,.58,1); `ease-in` = (.42,0,1,1).
- Lerp every property between the pair.

**Frame transform:** `M = S_c(baseScale) · T(p) · T(dx·512, dy·512) · R(rotate) · S(scaleX, scaleY) · T(−p)`, where `p = pivot × 512` and `S_c` is a scale about the canvas centre. This matches the prototype's outer `scale(base)` wrapper around the animated box with `transform-origin: pivot`.

**Particles** are drawn after the sticker, unscaled by `baseScale`, as in the prototype:
- **Hearts** (6):
  - Shape: the app-mark heart (`feature/namepack/engine/HeartPath.kt`) in #C23359.
  - x = 18, 34, 50, 66, 82, 28 % of the canvas; sizes 0.06 / 0.08 / 0.10 of the canvas (i % 3).
  - Each heart's phase is `((t_ms − i × 300) mod 1800) / 1800`:
    - y goes from 88% to 26%;
    - sway is ±0.04 × sin(2π·phase);
    - scale goes from 0.6 to 1;
    - opacity fades in over 0–15%, holds to 60%, then fades out.
- **Sparkle** (6 four-point stars):
  - Colour #F5C542 with a white core.
  - Positions (16,20), (82,16), (88,60), (12,66), (50,8), (70,88) %; sizes 0.05 / 0.07 / 0.09 (i % 3).
  - Each star lives 700 ms inside a 1,500 ms loop, starting at i × 133 ms.
  - Over its life, scale goes 0 → 1 → 0 and rotation 0 → 45°.

**Export:**
- `frames` frames at `t = i / frames`. Durations add up to `durationMs`, each at least 8 ms.
- The rest composite (the sticker with its outline) is rendered once; each frame is that bitmap under `M`, plus the particles.
- Encoding goes through the existing animated path. Quality starts at 60 and steps down; if that still misses 500 KB, frames are halved evenly (as for clips).
- Frame 0 is the rest pose (every preset's first keyframe is identity), which is what WhatsApp shows when it stops.

**Pack kind:** a pack is animated if any sticker is a clip or has a preset other than `none`. A still sticker inside an animated pack exports as **two identical frames** ⚠ (§13.1).

**Clips:** presets are disabled. Layers and the outline render on every clip frame (the per-frame cut-out pipeline stays).

## 8. Editor screen (Create › Cut out)

**Header:** unchanged: back, "Cut out", mono counter "n / N", Undo.

**Canvas card:**
- Unchanged: 350 dp square, radius 20, 1 dp #E7EAEF; the canvas is inset 27 dp, radius 14, with today's checker.
- **Zoom button:**
  - 36 dp circle, 10 dp from the inline end and the top.
  - Default: 1 dp #E7EAEF border, white fill, Ink `zoom-in` icon at 17 dp.
  - While zoomed: Rose fill and border, white `zoom-out`.
  - Shadow `0 2 6 rgba(20,22,28,.12)`. Content description "Zoom". The hints keep today's zoom texts.
- **Hint line:** 11 sp / 500 #626873, centred, 8 dp from the bottom. It shows only when no layer is selected. Per tool:
  - Auto, Brush, Erase: today's texts.
  - Add: `create_hint_add`.
  - Draw: `create_hint_draw`.
  - Animate: `create_hint_animate`, or `create_hint_animate_clip` on a clip.
  - While zoomed: `create_hint_zoomed`.
- **Selection** (a layer is selected):
  - **Box:** 2 dp dashed Rose, 6 dp outside the layer's bounds, radius 6, with a 1.5 dp white hairline outside and inside. It rotates with the layer and fades in over 120 ms.
  - **Handles** are 22 dp circles centred on the box corners, each inside a 44 dp touch target:
    - Delete, top-start: white fill, 2 dp Rose border, Rose `x` at 12 dp.
    - Edit, top-end, text only: white fill, 2 dp Rose border, Rose `pencil` at 11 dp.
    - Resize-and-rotate, bottom-end: Rose fill, 2 dp white border, white `scaling` at 11 dp. A one-finger drag scales by the distance to the centre and rotates by its angle.
    - Start and end mirror in RTL.
  - **Action pill** (replaces the hint line):
    - Ink fill, fully rounded, 2 dp inset, 2 dp gap, shadow `0 6 16 -8 rgba(0,0,0,.5)`, centred 6 dp from the bottom.
    - Buttons are 36 dp high, 12 dp side padding, with a 15 dp icon and an 11.5 sp / 600 white label 6 dp apart.
    - Actions: `copy` Duplicate · `flip-horizontal` Flip · `send-to-back` Behind (or `bring-to-front` In front when the layer is behind) · `trash-2` Delete.
- **Gestures on the canvas** (not in Brush, Erase or Draw):
  - Tap a layer: select it (the top-most hit; front layers first).
  - Tap empty space: deselect.
  - Drag a layer: move it.
  - Two-finger pinch and twist on the selected layer, or on any layer under the fingers: resize and rotate.
  - Double-tap a text layer: edit it.
  - In Draw, a one-finger drag draws a stroke. In Brush and Erase, touches edit the mask as today.
  - In Animate, a tap plays one loop when motion is reduced; otherwise taps select as above.

**Tool bar:**
- Same container: Ink, radius 16, 8 dp padding, 4 dp gap.
- Six buttons with equal weight, 54 dp high, radius 11, icon 20 dp, label 10 sp / 600 on a single line. Selected: Rose fill and white; idle: #C3C9D2.
- Icons: Auto uses today's `Wand2`; Brush `brush`; Erase `eraser`; Add `smile-plus`; Draw `pen-line`; Animate `circle-play`.
- **Label fallback (handoff):** if any label at 10 sp is wider than its slot, every label drops to 9.5 sp. If one still doesn't fit, labels hide and each button keeps its label as the content description.
- **Tapping a tool:**
  - Leaving Draw first turns the live strokes into one Drawing layer (§4).
  - **Auto:** switches to Auto, deselects, and re-runs the automatic cut-out, which resets Brush and Erase strokes. That is today's `selectTool(Auto)` and the prototype's `runAuto(force)`. Layers, outline and preset are kept, and the reset strokes leave the undo stack.
  - **Brush / Erase:** deselect; the size row shows.
  - **Add:**
    - Opens the Add sheet on its last tab. Tapping Add while the sheet is open closes it and returns to Auto.
    - An Emoji or Stickers pick also closes it.
    - System back closes the sheet before it leaves the screen.
  - **Draw:** deselects; the Draw row shows.
  - **Animate:** the preset strip and note show.

**Rows under the bar** (one at a time):
- **Brush or Erase:** today's size row.
- **Draw:**
  - A 46 dp row: radius 12, 1 dp #E7EAEF, 12 dp side padding, 7 dp gap.
  - Eight 22 dp swatches. The white swatch has a 1 dp #D8DCE3 border; the others 1 dp rgba(0,0,0,.08). The selected swatch gets a 2 dp white ring plus a 2 dp Rose ring.
  - A flexible space, then the size control: a #F3F5F8 track, radius 9, 2 dp padding and gap, three 34 × 28 cells with radius 7.
    - The selected cell is white with the shadow `0 1 2 rgba(0,0,0,.08)`.
    - Each cell has a dot of 7 / 10 / 13 dp, Ink when selected and #8B929D otherwise.
    - Content descriptions: Small, Medium, Large.
- **Animate:**
  - A horizontal strip of nine 56 dp rail tiles with a 10 dp gap, padded 4 dp on the sides and top and 20 dp at the bottom.
  - Each tile plays a 44 dp mini of the current decorated sticker in its preset, scaled by `baseScale`. The label (9.5 sp / 600) sits 17 dp below the tile; Rose when selected, #626873 otherwise.
  - Pressing a tile scales it to .96 over 90 ms and springs back over 140 ms.
  - Under the strip: `create_animate_note` (11.5 sp / 1.45, #8B929D, 6 dp overlap).
  - On a clip: the strip is at 40% opacity and disabled, and the note is `create_animate_note_clip`.
  - With reduced motion: the note starts with `create_animate_note_reduced`, and the tiles show frame 0.

**Outline row:**
- The same card as today, now a column with a 10 dp gap.
- Title "Outline" (14 sp / 600) and body `create_outline_body` (12 sp #8B929D), with today's switch.
- When on, a sub-row:
  - a Thin · Medium · Thick control (#F3F5F8 track, radius 9, 2 dp padding; 28 dp cells with radius 7 and 10 dp side padding; 12 sp / 600; selected white and Ink with the shadow, others #626873);
  - a flexible space;
  - five 22 dp swatches: White (default), Ink, Rose, Gold #F5C542, Sky #7CC4F5.

**Rail:**
- Tiles show the decorated sticker.
- Moving stickers carry an "ANIM" badge: 7.5 sp mono / 600, 0.05 em tracking, #626873 on white, 1 dp #E7EAEF, padding 1 × 4, radius 4, 4 dp outside the inline-start edge and 6 dp below the bottom.
- Tapping a tile first turns any live Draw strokes into a layer, then deselects.

**Footer:** unchanged. Next is disabled while any cut-out is pending, and turns live Draw strokes into a layer before navigating.

## 9. Add sheet

**Container:**
- An overlay pinned to the bottom of the editor (it covers the footer), not a modal: no scrim, and the canvas card above stays live and tappable.
- Surface fill, top radius 24, shadow `0 −10 30 −16 rgba(20,22,28,.3)`, padding 10 / 16 / 14, 10 dp gap. At most 442 dp high.
- It rides the IME inset.
- Motion: opens over 280 ms with cubic-bezier(.2,.7,.2,1) from the bottom; closes over 200 ms ease-in; tab content crossfades over 120 ms.
- Handle: 36 × 4, radius 2, #E1E5EB, centred. Dragging it down closes the sheet.
- Segmented control:
  - A #F3F5F8 track, radius 12, 3 dp padding. Cells 34 dp high, radius 9, 13 sp / 600.
  - The selected cell is white and Ink with the shadow `0 1 2 rgba(0,0,0,.08)`; the others #626873.
  - Tabs: Text · Emoji · Stickers.

**Text tab:**
- **Field:**
  - At least 46 dp, radius 12, a 1 dp Rose border while editing, 14 dp side padding, 9 dp gap.
  - A #8B929D `type` icon at 17 dp, 15 sp Ink input, placeholder `create_text_placeholder`.
  - A mono counter "n/30" (11 sp #A2A9B4). 30 characters, up to 2 lines.
- **Which layer it edits:**
  - The selected text layer, if any.
  - Otherwise the first character, or a phrase, creates a new text layer with the pending style, colour and font.
  - Clearing the field and closing the sheet removes an empty layer ⚠ (§13.2).
- **Quick phrases:** a horizontal row, 6 dp gap. Chips are 32 dp high, 12 dp side padding, fully rounded, 1 dp #E7EAEF, white, 12.5 sp / 600 Ink. A tap replaces the text.
- **Hidden while the keyboard is up:**
  - **Style chips:** four, equal weight, 58 dp high, radius 12.
    - Selected: #FFEEF0 fill with a 1 dp Rose border. Others: #F6F7F9 with a 1 dp #E7EAEF border.
    - Each shows "Aa" at 20 dp in that style (Bubble at .8 scale) and its label (10 sp / 600, #626873).
  - **Colour and font row:** eight 24 dp swatches, a flexible space, then three font chips.
    - Font chips are 32 dp high, 11 dp side padding, fully rounded. Each is drawn in its own font: Rounded in Baloo 2 800 at 13 sp; Hand in Caveat 700 at 15 sp; Display in Lilita One at 13 sp.
    - Selected chips are #FFEEF0 with Rose text and border; others white with Ink text and a #E7EAEF border.
- **Defaults** for a new text layer: Sticker style, Rose, Rounded, 52 px.
- **Keyboard:** the Done key or a tap on the canvas closes the keyboard; the sheet stays open.

**Emoji tab:**
- **Love row:** all 22 Love emoji, 40 dp each, horizontal scroll, 6 dp gap. Its content description is `create_emoji_love`.
- **Category chips:**
  - 36 dp high, 15 dp side padding, fully rounded, 13 sp / 600.
  - Selected: #FFEEF0, Rose text and border. Others: white, #626873, #E7EAEF border.
  - Categories: Smileys, Hearts, Hands, Animals, Food, Symbols, plus Recent at the end once anything has been used. Recent is persisted, most recent first, up to 18.
  - The first open shows Smileys.
- **Grid:** 6 per row, 6 dp gap, square tiles with radius 12, #F6F7F9 fill and 6 dp padding. It scrolls inside the sheet.
- **Tapping an emoji:** adds it at the centre, 30% width, closes the sheet, returns to Auto and records it in Recent. The content description is the glyph, so TalkBack reads its localised name.
- **Skin tones:** a long-press on an item with `skinTones` opens a popover above it:
  - White, 1 dp #E7EAEF, radius 14, 4 dp padding, 2 dp gap, shadow `0 10 24 −10 rgba(20,22,28,.35)`, with a 12 dp tail pointing at the item.
  - Six tones at 38 dp: Default (bundled), then Light, Medium-light, Medium, Medium-dark and Dark (downloaded on demand, each showing a spinner until it arrives).
  - Tapping a tone adds that tone.
  - If a download fails, the toast `create_toast_skin_failed` shows and those cells stay disabled.
  - A tap outside closes the popover.

**Stickers tab:**
- At most 290 dp high, scrolling, 8 dp gap.
- Section headers (Doodles, Props): 11 sp mono / 600, 0.08 em tracking, #8B929D, uppercase.
- Grid: 4 per row, 8 dp gap, square tiles with radius 14, a 1 dp #E7EAEF border, #F6F7F9 fill and 10 dp padding.
- **Tapping a piece:** adds it at its anchor with `defaultWidth`, then closes the sheet and returns to Auto.
- Content description: the piece's `label` ⚠ (English, §13.8).

## 10. Pack details

- Meta: "n stickers · Animated/Static · 512×512 WebP", where Animated follows §7's pack rule.
- Info line: `create_info_body_animated` for animated packs, today's `create_info_body` for still ones.

## 11. Settings › Licences

**Settings row:**
- Goes in About, between "More apps" and "Privacy policy".
- Uses the `scale` icon, the title `settings_licences` and a chevron, in the existing row style.
- Opens the Licences screen.

**Licences screen:**
- Header: 52 dp with back and the title "Licences" (16 sp / 700).
- Intro: `licences_intro`, 13 sp / 1.5, #626873.
- Card: white, radius 16, 1 dp #EEF0F4.
- Rows: 14 × 15 dp padding, 13 dp gap, 1 dp #F2F4F7 bottom rule.
  - Name: 14.5 sp / 600 Ink.
  - Sub: 12.5 sp / 1.45 #8B929D, 2 dp below.
  - Tag: 9 sp mono / 600, 0.06 em tracking, #626873 on #F3F5F8, 1 dp #E7EAEF, 3 × 6 dp padding, radius 5.
- Rows, in order:
  1. "Fluent Emoji" — `licences_fluent_sub` — MIT.
  2. "Baloo 2 · Baloo Bhaijaan 2 · Rubik · Caveat · Lilita One · Mirza · Kalam · Amatic SC · Lalezar · Yatra One · Ruslan Display · Karantina" — `licences_fonts_sub` — SIL OFL 1.1.
  3. "Lucide" — `licences_lucide_sub` — ISC.

## 12. Frames (Screens §06→07), which must all be reproducible

| # | Frame | Proves |
|---|---|---|
| 1 | Editor, default | new bar, Zoom on the card, a Sticker-style caption and a heart emoji layer, the outline wrapping both |
| 2 | Add › Text, typing | sheet with the keyboard up, the field with its counter, phrases |
| 3 | The same in Arabic | full RTL; the caption in Baloo Bhaijaan 2 |
| 4 | Add › Emoji | Love row, chips, grid, skin popover on a hand |
| 5 | Add › Stickers | Doodles and Props, 4 per row |
| 6 | A selected layer, mid-drag | box, handles, centre guide, action pill |
| 7 | Draw, mid-doodle | Draw row, strokes with an ink edge |
| 8 | Animate · Heartbeat | strip, canvas playing, note |
| 9 | Outline · Thick, Rose | sub-row |
| 10 | Rail | decorated tiles and the ANIM badge |
| 11 | Brush active | layers dimmed to 40% |
| 12 | A video clip | strip disabled, the clip note |
| 13 | Pack details, animated | "Animated" and "under 500 KB each" |
| 14 | Settings › Licences | the list |

## 13. Decisions and deviations (⚠)

1. **Still stickers in animated packs are 2 identical frames.** The handoff says "single-frame animations", but WhatsApp's own validator rejects one-frame stickers in an animated pack. The app already muxes two frames, and that stays.
2. **Typing with no text layer selected makes a new layer.** The prototype edits the last text layer, which gives no way to add a second one. An empty layer is removed when the sheet closes.
3. **Auto keeps today's behaviour:** it re-runs the cut-out and resets Brush and Erase strokes. What's new is that layers survive the reset.
4. **Animate tiles show the decorated sticker,** as the caption describes ("the current sticker"). The prototype shows the bare cut-out.
5. **Snap guides on both axes.** The prototype shows only the vertical centre guide. The horizontal guide appears only while the y-snap is engaged.
6. **Entering Draw deselects the current layer,** so no selection box sits over the drawing.
7. **Emoji ship as WebP (q 86)** instead of PNG, which saves several MB. The 256 px art is unchanged.
8. **Decoration labels are English.** They are only accessibility labels; translating 54 labels into 18 languages is left for later. Emoji use their glyph instead, which TalkBack localises.
9. **Caveat on API 24-25 renders as fake bold.** It ships as a variable font, and weight variations need API 26.
10. **Recent keeps up to 18 emoji,** stored in prefs. The design leaves the size open.
11. **Layer size bounds use the longer side.** A layer's longer side stays within 10–100% of the canvas (emoji 50%), not its rendered width: with a width floor and a longer-side cap a thin upright piece could not be resized at all. A new layer starts at min(1, cap); the floor only stops shrinking in a gesture and never forces growth. A flushed drawing is never capped below its drawn size.
12. **Layer tools wait for the cut-out.** Add, Draw and Animate are dimmed and inert while the active sticker's cut-out is pending or the decor data has not loaded, and a rail switch onto a pending sticker falls back to Auto. Pieces are placed against the subject box, which only exists once the cut has landed.
13. **Auto re-runs the cut-out only when tapped while already active.** Switching to Auto from another tool just switches, because Auto is also the way back to moving layers and a re-run would discard the Brush and Erase work with its undo steps. (Replaces the first sentence of decision 3.)
14. **A flushed drawing arrives deselected.** The flush is a side effect of leaving Draw, and a selection box would sit in the next tool's way.
15. **Reaching layers under a frame-like piece.** A gesture that starts inside the selected layer's box moves the selected layer even when another layer lies on top; a tap where the selected layer and others are under the point selects the next one down (wrapping); a double tap prefers the selected text layer.
16. **The action pill moves to the top of the card** when the selected layer's box (with its handles) would overlap it at the bottom, decided only while no gesture is live. A caption at its default position otherwise put its resize handle on Delete.
17. **Captions take their direction from their own letters** (first-strong, with the app language as the fallback for text with no strong letter). The prototype let the UI language decide, which lettered a Latin "OK!" as "!OK" in Arabic.
18. **Rows that don't fit the reference width.** The Draw row narrows its size cells (never under 20 dp) and then its gaps; the Text tab's colour and font row wraps to two lines on every phone; the outline sub-row tightens its cell padding before stacking. The spec's measurements add up to more than 390 dp.
19. **Thickness changes snap** while the outline switch and colour crossfade over 160 ms: the subject silhouette is re-dilated for the new radius, so there is no old shape to fade from.
20. **In Add › Text, a tap on empty canvas keeps the selected caption** (the keyboard still closes). With the keyboard up the style chips are hidden, and the natural tap to drop it would otherwise deselect the caption being styled.
21. **A second export after a cancelled WhatsApp confirm** exports again when anything changed since, and replaces the earlier saved pack unless WhatsApp already has it. Back is blocked while an export is busy.
