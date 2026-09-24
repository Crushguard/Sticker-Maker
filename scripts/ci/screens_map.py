#!/usr/bin/env python3
"""Builds README.md for the `screenshots` branch: every design frame next to
the app screenshot the on-device tour captured for it, plus a navigation map.

Reads <root>/app/manifest.json (written by ScreenTourTest) and, when present,
<root>/design/manifest.json (the rendered design frames).
"""
import argparse
import datetime
import json
import os

FLOW = """```mermaid
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
```"""


def load(path, default):
    if not os.path.exists(path):
        return default
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def cell_image(path, root, width=210):
    if path and os.path.exists(os.path.join(root, path)):
        return f'<img src="{path}" width="{width}">'
    return "—"


def one_line(text):
    return " ".join((text or "").split()).replace("|", "\\|")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--commit", default="")
    parser.add_argument("--branch", default="")
    parser.add_argument("--run-url", default="")
    parser.add_argument("--outcome", default="")
    args = parser.parse_args()

    root = args.root
    app = load(os.path.join(root, "app", "manifest.json"), {"device": {}, "shots": []})
    design = load(os.path.join(root, "design", "manifest.json"), [])

    shots = app.get("shots", [])
    by_frame = {s["frame"]: s for s in shots if s.get("frame") and s.get("status") == "pass"}
    extras = [s for s in shots if not s.get("frame") and s.get("status") == "pass"]
    failures = [s for s in shots if s.get("status") == "fail"]
    frames = {int(d["n"]): d for d in design}
    numbers = sorted(set(frames) | set(by_frame) | set(range(1, 43)))

    device = app.get("device", {})
    captured = sum(1 for n in numbers if n in by_frame)
    date = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    short = args.commit[:7]

    out = []
    out.append("# Love Stickers · every screen, mapped\n")
    out.append(
        "Screenshots of the real app on an Android emulator, next to the design frame each one "
        "implements (`design/Screens.dc.html`: 13 screens, 42 frames in flow order). "
        "The on-device tour (`app/src/androidTest/.../tour/ScreenTourTest.kt`) drives the app "
        "like a user and checks each state before it takes the screenshot.\n"
    )
    status = "passed" if args.outcome == "success" else (args.outcome or "unknown")
    out.append(f"- **Result:** {captured} of {len(numbers)} design frames captured, "
               f"{len(extras)} extra states, {len(failures)} failed steps · tour {status}")
    if args.branch or short:
        out.append(f"- **Source:** `{args.branch}` @ `{short}`")
    if args.run_url:
        out.append(f"- **Captured by:** [CI run]({args.run_url}) · {date}")
    if device:
        out.append(
            f"- **Device:** {device.get('model', '?')}, Android API {device.get('sdk', '?')}, "
            f"{device.get('widthPx', '?')}×{device.get('heightPx', '?')} px at {device.get('densityDpi', '?')} dpi"
        )
    out.append("- **Backend:** Firestore and Storage emulators seeded by `scripts/upload-pack.js` "
               "(14 packs, 8 themes); the debug build points at them with `-PfirebaseEmulatorHost=10.0.2.2`")
    out.append("- **WhatsApp:** a test double (`testing/whatsapp-stub`) that reads each pack back "
               "through the app's ContentProvider and checks WhatsApp's pack rules before answering")
    out.append("- **Test log:** [`app/instrument.txt`](app/instrument.txt)\n")
    out.append("Design frames are rendered from `design/Prototype.dc.html`. The prototype's sample "
               "photos and own-pack stickers were never exported with the design, so frames 18–24 and "
               "26–28 show empty tiles on the design side; their layout is still the reference.\n")

    out.append("## Navigation map\n")
    out.append(FLOW + "\n")

    sections = []
    for n in numbers:
        section = (frames.get(n) or by_frame.get(n) or {}).get("section", "")
        if section not in sections:
            sections.append(section)

    for section in sections:
        out.append(f"## {section}\n")
        out.append("| Frame | Design | App | Route · what the tour verified |")
        out.append("|---|---|---|---|")
        for n in numbers:
            ref = frames.get(n) or {}
            shot = by_frame.get(n)
            if (ref or shot or {}).get("section", "") != section:
                continue
            title = ref.get("title") or (shot or {}).get("title", "")
            mark = "✅" if shot else "❌"
            design_img = cell_image(os.path.join("design", ref["file"]) if ref.get("file") else None, root)
            app_img = cell_image(os.path.join("app", shot["file"]) if shot else None, root)
            if shot:
                detail = f"`{shot.get('route', '')}` · {one_line(shot.get('notes'))}"
            else:
                detail = "Not captured in this run."
            out.append(f"| {mark} **{n:02d}** {one_line(title)} | {design_img} | {app_img} | {detail} |")
        out.append("")

    if extras:
        out.append("## Extra states\n")
        out.append("States the design shows only in passing, or checks worth keeping a picture of.\n")
        out.append("| Key | App | Route · what the tour verified |")
        out.append("|---|---|---|")
        for s in extras:
            out.append(f"| **{s['key']}** {one_line(s['title'])} | {cell_image(os.path.join('app', s['file']), root)} "
                       f"| `{s.get('route', '')}` · {one_line(s.get('notes'))} |")
        out.append("")

    if failures:
        out.append("## Failed steps\n")
        out.append("| Step | Screen at failure | Error |")
        out.append("|---|---|---|")
        for s in failures:
            out.append(f"| {one_line(s['title'])} | {cell_image(os.path.join('app', s['file']), root)} "
                       f"| {one_line(s.get('error'))} |")
        out.append("")

    with open(os.path.join(root, "README.md"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(out))
    print(f"README.md: {captured}/{len(numbers)} frames, {len(extras)} extras, {len(failures)} failures")


if __name__ == "__main__":
    main()
