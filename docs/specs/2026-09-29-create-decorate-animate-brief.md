# Create › Cut out: Add, Draw and Animate

You designed Love Stickers, including the Create flow (§06) and its Cut out editor. Please **extend** that editor so people can decorate their cut-out and make it move.

This is an addition, not a redesign. Keep the layout, tokens, type, components and copy voice as they are. Update the Prototype's `editor` screen in place, since the existing Cut out frames embed it. Put frames for the new states in a new section right after §06, called "Create · decorate & animate". Don't remove or repurpose any other frame or pack.

## Why (research, Sep 2026)

- **The showcase promises more than the editor makes.** Your "Made by you" packs (Us, always and Just Us) show crowns, halos, horns, marker doodles, bold captions and motion. Today the editor makes a cut-out plus one caption of up to 16 characters in one style. This work closes that gap.
- **Animation is the top request.** About 12% of ~4,900 sticker-maker Play reviews ask for animated stickers. We found no major WhatsApp sticker maker that animates a still photo in one tap; they all need a video or GIF.
- **Text and outline requests.** Users want more fonts and colours, text they can resize and rotate, outline colour and thickness, and outlines on animated stickers too. Arabic reviewers mostly complain about apps with no Arabic at all, and ask for Arabic fonts.
- **What WhatsApp's own maker does.** It has three separate tools: Stickers (with an Emoji switch), Text and Draw. It has no outline and no animation.
- **Art sources.** None of the stock or GIF libraries we checked (Freepik, Flaticon, GIPHY and others) lets its art be baked into stickers that people share. So all decoration art is yours, and emoji come from Microsoft's Fluent Emoji (MIT licence).

## Hard constraints

- **On-device only.** Everything renders on the phone: no AI, no server rendering, no per-user cost.
- **WhatsApp rules:**
  - Stickers are 512×512 WebP: static up to 100 KB; animated up to 500 KB and 10 s.
  - The first frame of an animated sticker must show the complete sticker, because WhatsApp stops on it.
  - A pack is all static or all animated.
  - Each sticker carries 1–3 emoji tags.
- **Frame budget.** Plan for 12–16 frames per animated loop (about 12 fps) so photo stickers fit in 500 KB. Design motion that reads well at that frame rate.
- **Languages.** The app ships 19 languages, including RTL ones (Arabic, Persian, Urdu, Pashto, Hebrew). Every new label must fit the longest locale on a 360 dp-wide phone.
- **File size.** Keep each file you hand over at or under 240 KB (the sync cap).

## 1. Tool bar and Zoom

- **Six tools** in the same dark bar, with the same button spec: **Auto · Brush · Erase · Add · Draw · Animate**.
  - Add takes Text's slot. Suggested Lucide icons: `smile-plus` for Add, `pen-line` for Draw, `circle-play` for Animate. Your call.
- **Zoom** leaves the bar and becomes a round magnifier button in the top-end corner of the canvas card.
  - Same toggle, same 1.6× zoom and pinch, same hints. The button turns Rose while zoomed in.
- **Label fit.** Check the six labels at 360 dp in German, Russian and Burmese. Give a fallback (for example 9.5 sp) if one doesn't fit.

## 2. Layers (everything that gets added)

Text, emoji, stickers and drawings all become layers on the canvas.

- **Placement**
  - A new layer drops at the canvas centre, already selected.
  - The first text layer goes to the bottom centre, where today's caption sits.
  - Head pieces (crown, halo, horns, ears, flower crown) drop onto the top edge of the cut-out.
- **Selection**
  - Tapping a layer selects it and shows a dashed selection box that reads on the checker.
  - The box has a delete handle (top-start) and a resize-and-rotate handle (bottom-end). Text layers also get an edit handle (top-end).
  - Mirror the handles in RTL. They can look small, but each needs a touch target of at least 44 dp.
- **Gestures**
  - Drag moves, pinch resizes, twist rotates.
  - Double-tap edits text; tapping empty canvas deselects.
  - A Rose centre guide appears and the layer snaps when it crosses the canvas centre. Rotation snaps to 0°.
- **Actions.** The selected layer gets a small action row: Duplicate · Flip · Behind/In front · Delete. It could sit where the hint line is, or float as a pill if that reads better.
  - "Behind" puts the layer under the cut-out, for example hearts peeking out from behind someone.
- **Limits**
  - Up to 8 layers per sticker, with a toast when the limit is reached.
  - At least a quarter of every layer stays inside the canvas.
- **Interaction with other tools**
  - While Brush or Erase is active, layers dim to about 40% and ignore touches.
  - While zoomed, pinch and pan move the view; a one-finger drag still moves a layer.
- **Undo.** The header's Undo steps back through everything, one touch at a time: strokes, adding, moving, resizing and deleting layers, text and style edits, and outline and animation changes.
- **Outline.** The outline wraps the subject and every layer together, so the sticker stays one die-cut shape like the catalog art.
- **Rail.** Rail tiles show the decorated sticker.

## 3. Add sheet: Text · Emoji · Stickers

**The sheet itself**
- It is your bottom sheet (Surface, top radius 24), headed by the segmented control from Import (Photos · Camera · Video).
- It opens on the tab used last.
- It has no dark scrim over the canvas and is short enough that the canvas stays visible and live above it, including while the keyboard is up.

**Text**
- **Field.** Today's caption field, now 30 characters and up to two lines, auto-fitted.
- **Quick phrases.** A row of chips with short, name-free captions in the app language: love you, miss you, good morning, good night, kiss, hug, sorry, be mine, call me, plus casual ones such as omw, ok babe and mine.
  - Supply them like the Custom Stickers `phrases.json`: en, ar, fr and hi from you; we translate the rest.
- **Styles.** Four style chips, each showing a live "Aa":
  - Classic: today's caption look.
  - Sticker: colour fill with a white die-cut edge, like the catalog lettering.
  - Bold stroke: white fill with a thick ink stroke.
  - Bubble: text inside a speech bubble.
- **Colours.** Eight swatches: white, ink, rose and five of the category hues.
- **Fonts**
  - The default is the lettering stack chosen for Custom Stickers: Baloo 2, Baloo Bhaijaan 2 and Rubik ExtraBold, picked by script.
  - Propose up to two more font moods, for example handwritten and display. Map each one per script (Latin, Arabic script, Devanagari, Cyrillic, Hebrew), SIL OFL only.
  - Include one Arabic display option.

**Emoji**
- **Art.** Microsoft Fluent Emoji, 3D style (MIT). The app bundles a curated love-and-feelings subset of about 300 and downloads the rest on demand. Design the tab around this look.
- **Size cap.** The art is 256 px, so limit an emoji to about half the canvas width.
- **Layout.** A love-first row, then category tabs (Smileys, Hearts, Hands, Animals, Food, Symbols), plus a Recent row after first use.
- **Skin tones.** Long-press a hand or person emoji to choose a skin tone.
- **Placing.** Tapping an emoji places it and closes the sheet.
- **Tags.** Emoji the user adds also become the sticker's WhatsApp emoji tags. This needs no UI.

**Stickers**
- **Your own decoration set**, about 30 pieces in the marker style of `us-doodle-*`.
  - Draw what emoji can't do. Don't redraw objects the emoji set already covers (rose, ring, teddy, love letter, kiss mark).
- **Doodles:** crown, halo, devil horns, cat ears, bunny ears, heart cluster, sparkle burst, blush marks, tears, sweat drop, anger mark, dizzy stars, zzz, motion lines, arrow, "!!", "?".
- **Props:** speech bubble, thought bubble, heart glasses, flower crown, blank ribbon banner (a text layer goes on top), a heart outline to go around the subject, cupid's arrow.
- **No English word art.** Words belong to Text, so they get translated.
- **Grid.** Four pieces per row, with section headers.

## 4. Draw

- **Marker.** Draw is a marker for the user's own doodles and handwriting.
- **Controls.** They sit under the bar, where the brush slider sits today: the same eight colour swatches and a three-step size slider.
- **Stroke look.** Define the marker look so white strokes still read against a white outline, for example with a thin ink edge.
- **Undo and grouping.** Undo removes the last stroke. Leaving Draw turns the strokes into one layer that can be moved, resized or deleted.

## 5. Animate

- **Preset strip.** A strip of 56 dp tiles in the rail-tile style sits under the bar.
  - Each tile plays the current sticker in that preset. The selected tile gets the Rose 2 dp border.
  - The canvas plays the selected loop.
- **Whole sticker.** A preset animates the whole sticker (subject, layers and outline) as one piece. Per-layer motion is not in v1.
- **Suggested set**, nine tiles at most, renamed or replaced as you see fit:
  - None, Heartbeat, Wiggle, Bounce, Float, Jelly, Shake.
  - Two effects that keep the sticker still and add motion around it: Hearts (small hearts rise and fade, drawn with the app-mark heart) and Sparkle (twinkles).
- **Loop rules**
  - Each loop runs 0.8–2 s and is seamless.
  - Frame 0 is the rest pose with the full sticker visible.
  - Nothing leaves the canvas. Cut-outs often fill the canvas, so say how far each preset needs the base sticker shrunk (for example to 88%).
- **Pack note.** One line under the strip explains that a single moving sticker makes this an animated pack, because WhatsApp doesn't mix still and moving stickers. Still stickers stay still.
- **Video clips** already move. Show the strip disabled with a short line explaining why. Layers still work on clips.
- **Reduced motion.** Tiles show a still frame, and the canvas plays only when tapped.
- **Rail badge.** Mark animated stickers in the rail with a small badge in keeping with the mono ANIMATED pill.

## 6. Outline styles

- **Row.** The "White outline" row becomes "Outline", with the same switch.
- **Options.** When the switch is on, a sub-row offers thickness (Thin · Medium · Thick) and colour: white by default, plus ink, rose and one or two more.
- **Copy.** Keep the line saying WhatsApp recommends white.
- **Animated stickers.** The outline also applies to animated stickers.

## 7. Pack details

- For animated packs the info line must say "under 500 KB each". Today it always says 100 KB.

## 8. Settings › Licences

- A new Licences row in Settings opens a plain list of credits: Fluent Emoji (MIT), the lettering fonts (SIL OFL 1.1) and Lucide icons (ISC).
- Use the existing list-row style.

## Frames to add

1. Editor, default state: the new bar, Zoom on the canvas, and a sticker with a caption and an emoji, with the outline wrapping both.
2. Text tab while typing, with the keyboard up and the canvas visible.
3. The same with an Arabic caption, as a full RTL screen.
4. Emoji tab, including the skin-tone popover.
5. Stickers tab.
6. A selected layer: handles, the action row, and the centre guide mid-drag.
7. Draw mode, mid-doodle.
8. Animate, with Heartbeat selected and the pack note showing.
9. Outline expanded, set to Thick and Rose.
10. Rail with decorated and animated tiles.
11. Brush active, with layers dimmed.
12. A video clip, with Animate disabled.
13. Pack details for an animated pack.
14. Settings › Licences.

## Developer handoff (same depth as Custom Stickers)

- **Measurements** in the 390×844 frame (1 px = 1 dp, font px = sp). Use the existing colour tokens and Lucide icon names.
- **Copy.** Every new string in English with notes for translators, plus the Arabic used in the RTL frame.
- **Decoration art: `assets/decor/{set}-{n}.webp`**
  - Each piece is trimmed on a transparent canvas, longest side 512 px, at most 60 KB.
  - Generate them on flat #00FF00 and chroma-key them, as in DESIGN_NOTES.
- **Decoration index: `assets/decor/decor.json`** with, per piece:
  - `file`, `set`, and `label` (English, for accessibility).
  - `anchor`: `center` or `head-top`.
  - `defaultWidth`: a fraction of the canvas.
  - `emojis`: 1–2, used as WhatsApp tags.
- **Emoji subset: `assets/emoji/subset.json`**
  - The ~300 bundled code points, in display order, grouped by tab. The Love row comes first.
- **Motion presets: `assets/motion/presets.json`**
  - Per preset: `id`, `label`, `durationMs`, `frames`, `baseScale`, `pivot{x,y}`.
  - Keyframes as `[{t, scaleX, scaleY, rotate, dx, dy, easing}]`.
  - For effects, a particle spec: shape, count, colour, size, spawn area, path, `lifetimeMs`.
  - The app renders the frames from this, so no animated files are needed.
- **Text styles as data:** fill, stroke width and colour, shadow, and bubble shape, plus the font map per script.
- **Motion timings** for opening and closing the sheet, selection, snapping, and tile press states.
