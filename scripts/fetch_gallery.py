"""Download the 93 DeepSeek whale-girl illustrations used by the app.

Usage:
    python scripts/fetch_gallery.py

The images are written to app/DafeiyuChat/assets/ so `build.ps1` can bundle
them. The source repo is:
    https://github.com/AATINF/deepseek-whale-girl-assets
"""
import concurrent.futures
import json
import os
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_ASSETS = os.path.join(ROOT, "app", "DafeiyuChat", "assets")
RAW = "https://raw.githubusercontent.com/AATINF/deepseek-whale-girl-assets/main/"
SHEETS = ["02-core-47.jpg", "03-highres-14.jpg", "04-final-24.jpg"]


def download(url, path):
    request = urllib.request.Request(url, headers={"User-Agent": "dafeiyu-phone/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read()
    with open(path, "wb") as handle:
        handle.write(data)
    return len(data)


def main():
    os.makedirs(APP_ASSETS, exist_ok=True)
    sheet_dir = os.path.join(ROOT, "docs", "sheets")
    os.makedirs(sheet_dir, exist_ok=True)
    with open(os.path.join(APP_ASSETS, "gallery.json"), encoding="utf-8") as handle:
        names = json.load(handle)
    # gallery.json contains the variant filenames, e.g. "C01.jpg".
    variants = [name.rsplit(".", 1)[0] for name in names]

    jobs = []
    total = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        for variant in variants:
            target = os.path.join(APP_ASSETS, variant + ".jpg")
            jobs.append(pool.submit(download, RAW + "share-1600/" + variant + ".jpg", target))
        for sheet in SHEETS:
            target = os.path.join(sheet_dir, sheet)
            jobs.append(pool.submit(download, RAW + "sheets/" + sheet, target))
        for job in concurrent.futures.as_completed(jobs):
            try:
                total += job.result()
            except Exception as error:
                print("download failed:", error, file=sys.stderr)
                return 1

    print("downloaded %d files, %.1f MB" % (len(jobs), total / 1024.0 / 1024.0))
    print("assets:", APP_ASSETS)
    return 0


if __name__ == "__main__":
    sys.exit(main())
