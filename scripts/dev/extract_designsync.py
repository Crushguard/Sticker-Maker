#!/usr/bin/env python3
"""Decode DesignSync get_file results from Claude session transcripts to files.

Usage: python3 extract_designsync.py <manifest.json>
Manifest: {"design/path.webp": "/abs/dest/path.webp", ...}

Walks every *.jsonl transcript in the Claude project dirs, parses each line as
JSON, collects tool_result texts, and json-parses each text that looks like a
DesignSync get_file payload. Results persisted by the harness to tool-results
files ("Full output saved to: <path>") are followed and parsed too. Last
result per design path wins. Writes destinations, verifies magic bytes.
"""
import base64, glob, json, os, re, sys

PROJ_DIRS = glob.glob(os.path.expanduser("~/.claude/projects/*Sticker-Maker*"))
MAGIC = (b"RIFF", b"\xff\xd8\xff", b"\x89PNG", b"<!DO", b"<htm", b"# Pr", b"# ")
SAVED_RE = re.compile(r"saved to: (\S+\.txt)")


def iter_result_texts():
    for d in PROJ_DIRS:
        for path in glob.glob(os.path.join(d, "**", "*.jsonl"), recursive=True):
            try:
                with open(path, "r", errors="replace") as f:
                    for line in f:
                        try:
                            env = json.loads(line)
                        except json.JSONDecodeError:
                            continue
                        msg = env.get("message") or {}
                        content = msg.get("content")
                        if not isinstance(content, list):
                            continue
                        for item in content:
                            if not isinstance(item, dict) or item.get("type") != "tool_result":
                                continue
                            inner = item.get("content")
                            texts = []
                            if isinstance(inner, str):
                                texts.append(inner)
                            elif isinstance(inner, list):
                                texts += [c.get("text", "") for c in inner if isinstance(c, dict)]
                            for t in texts:
                                yield t
            except OSError:
                continue


def parse_payload(text):
    text = text.strip()
    if '"method"' in text and "get_file" in text:
        try:
            obj = json.loads(text)
            if isinstance(obj, dict):
                return obj
        except json.JSONDecodeError:
            pass  # persisted-output preview: fall through to "saved to:" path
    m = SAVED_RE.search(text)
    if m and os.path.exists(m.group(1)):
        try:
            return json.load(open(m.group(1)))
        except Exception:
            return None
    return None


def extract_results():
    out = {}
    for text in iter_result_texts():
        obj = parse_payload(text)
        if obj and obj.get("method") == "get_file" and "content" in obj:
            out[obj.get("path")] = (obj["content"], obj.get("isBase64", False))
    return out


def main():
    manifest = json.load(open(sys.argv[1]))
    results = extract_results()
    missing, bad, ok = [], [], []
    for design_path, dest in manifest.items():
        if design_path not in results:
            missing.append(design_path)
            continue
        content, is_b64 = results[design_path]
        data = base64.b64decode(content) if is_b64 else content.encode()
        if not data.startswith(MAGIC):
            bad.append((design_path, data[:8].hex()))
            continue
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, "wb") as f:
            f.write(data)
        ok.append((dest, len(data)))
    for dest, size in ok:
        print(f"OK {size:>7} {dest}")
    print(f"summary: {len(ok)} ok, {len(missing)} missing, {len(bad)} bad")
    if missing and "-q" not in sys.argv:
        print("MISSING:", *missing, sep="\n  ")
    if bad:
        print("BAD MAGIC:", *[f"  {p} -> {h}" for p, h in bad], sep="\n")
    sys.exit(1 if (missing or bad) else 0)


if __name__ == "__main__":
    main()
