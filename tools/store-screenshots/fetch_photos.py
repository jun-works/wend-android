"""Download explicitly free Unsplash photos; retain source and license provenance."""
import hashlib
import html
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PHOTOS = [
    ("dolomites", "lake-near-snow-covered-mountain-during-daytime-vfWAYeKye58", "Connor Stirling"),
    ("tre-cime", "landscape-photo-of-mountains-and-lake-OzRUBg8_A3k", "GEORGE ALEXANDRU NOVAC"),
    ("braies", "a-beautiful-mountain-lake-with-dock-zWTmywY3nyg", None),
    ("iceland", "waterfalls-during-daytime-Ku8DoAaeyjc", "Camila Camacho"),
]

def fetch(url):
    return subprocess.check_output(["curl", "-L", "--fail", "--silent", "--show-error", "--max-time", "60", url])

def main():
    folder = ROOT / "assets"
    folder.mkdir(exist_ok=True)
    records = []
    for name, slug, author in PHOTOS:
        page = "https://unsplash.com/photos/" + slug
        source = fetch(page).decode()
        if "Free to use under" not in source or "Unsplash License" not in source:
            raise RuntimeError("Free license not confirmed: " + page)
        image = html.unescape(re.search(r'<meta property="og:image" content="([^"]+)"', source)[1]).split("?")[0]
        if author is None:
            author = html.unescape(re.search(r'<meta property="og:title" content="Photo by (.*?) on Unsplash"', source)[1])
        url = image + "?auto=format&fm=jpg&w=1600&q=90"
        data = fetch(url)
        (folder / (name + ".jpg")).write_bytes(data)
        records.append(dict(file=name + ".jpg", author=author, source=page, download=url,
                            license="https://unsplash.com/license", verified="2026-10-16",
                            sha256=hashlib.sha256(data).hexdigest()))
        print(name, author, len(data))
    (folder / "sources.json").write_text(json.dumps(records, indent=2, ensure_ascii=False) + "\n")

if __name__ == "__main__":
    main()
