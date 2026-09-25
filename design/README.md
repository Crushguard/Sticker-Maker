# Design reference frames

The 42 frames of `design/Screens.dc.html` ("Every screen, every state"), rendered
from `design/Prototype.dc.html` at 390×844 CSS px and scaled to 780×1688. Each
frame is the prototype pinned to the state its `<dc-import>` props describe
(`manifest.json` lists them).

The prototype normally runs on claude.ai/design's canvas runtime, which is not
part of the repository. `renderer/shim.js` reimplements the small part it uses
(the `DCLogic` component base and the `sc-if` / `sc-for` / `{{ }}` template) on
React 18, and `renderer/render.mjs` screenshots each frame with Playwright,
using the app's own Hanken Grotesk and JetBrains Mono fonts.

Re-run from a checkout of the app repository:

```sh
cd design/renderer && npm install
REPO=/path/to/Sticker-Maker FRAMES=/path/to/design_frames.json node render.mjs
```

Known gap: the prototype references 53 images that were never exported with the
design (`assets/photos/p1–p12.jpg`, `assets/cutouts/*.webp` and the own-pack
stickers `us-*`, `couple-*`). Frames 18–24 and 26–28 therefore show empty or
broken tiles; their layout, copy and states are still the reference.
