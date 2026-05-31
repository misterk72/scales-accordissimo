#!/usr/bin/env python3
import argparse
import json
import shutil
import sys
import urllib.request
from pathlib import Path
from urllib.parse import urlparse


ASSETS_ROOT = Path("app/src/main/assets")
SCALES_ROOT = ASSETS_ROOT / "scales"


def fail(message: str) -> None:
    print(f"error: {message}", file=sys.stderr)
    raise SystemExit(1)


def safe_asset_path(asset_path: str) -> Path:
    target = (ASSETS_ROOT / asset_path).resolve()
    scales_root = SCALES_ROOT.resolve()
    if not str(target).startswith(str(scales_root) + "/"):
        fail(f"assetPath must stay under {SCALES_ROOT}: {asset_path}")
    if target.suffix.lower() != ".mp3":
        fail(f"only .mp3 assets are supported: {asset_path}")
    return target


def copy_or_download(source: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    parsed = urlparse(source)
    if parsed.scheme in {"http", "https"}:
        with urllib.request.urlopen(source, timeout=60) as response:
            content_type = response.headers.get("Content-Type", "")
            if "audio" not in content_type and "octet-stream" not in content_type:
                print(f"warning: unexpected content type for {source}: {content_type}", file=sys.stderr)
            with target.open("wb") as output:
                shutil.copyfileobj(response, output)
        return

    source_path = Path(source).expanduser()
    if not source_path.exists():
        fail(f"source file does not exist: {source}")
    shutil.copy2(source_path, target)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Import authorized MP3 scale accompaniments into the Android assets tree."
    )
    parser.add_argument(
        "source_manifest",
        help="JSON file containing entries with sourceUrl or sourceFile and assetPath.",
    )
    args = parser.parse_args()

    entries = json.loads(Path(args.source_manifest).read_text(encoding="utf-8"))
    if not isinstance(entries, list):
        fail("source manifest must be a JSON array")

    imported = 0
    for entry in entries:
        if not isinstance(entry, dict):
            fail("each source manifest entry must be an object")
        asset_path = entry.get("assetPath")
        source = entry.get("sourceUrl") or entry.get("sourceFile")
        if not asset_path or not source:
            fail("each entry must contain assetPath and sourceUrl or sourceFile")
        target = safe_asset_path(asset_path)
        copy_or_download(source, target)
        imported += 1
        print(f"imported {asset_path}")

    print(f"done: imported {imported} mp3 file(s)")


if __name__ == "__main__":
    main()
