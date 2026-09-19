"""Download public API/font audit samples. Usage: script OUTPUT_DIR PAGE [PAGE...].

No fonts are bundled into the app or committed. Requests use bounded concurrency.
"""
import concurrent.futures
import json
from pathlib import Path
import subprocess
import sys
import threading

chapter_lock = threading.Lock()

output = Path(sys.argv[1])
output.mkdir(parents=True, exist_ok=True)
pages = [int(p) for p in sys.argv[2:]]
assert pages and all(1 <= page <= 604 for page in pages)


def download(url, path):
    subprocess.run(["curl", "-fsSL", "--retry", "1", "--connect-timeout", "20",
                    "--max-time", "90", "-o", str(path), url], check=True)


def fetch(page):
    body = output / f"page{page}.json"
    download(f"https://api.quran.com/api/v4/verses/by_page/{page}?words=true&word_fields=code_v2,text_uthmani&per_page=50&mushaf=1", body)
    data = json.loads(body.read_text())
    # Never claim an audit covered a whole page if the response was truncated.
    assert data["pagination"]["next_page"] is None, f"Pagination required for {page}"
    if len(data["verses"]) != data["pagination"]["total_records"]:
        # Keep the original broken response: the audit must exercise recovery.
        chapters = [int(v["verse_key"].split(":")[0]) for v in data["verses"]]
        with chapter_lock:
            for chapter in range(min(chapters), max(chapters) + 1):
                verses, batch = [], 1
                while True:
                    target = output / f"chapter{chapter}.json"
                    download(f"https://api.quran.com/api/v4/verses/by_chapter/{chapter}?words=true&word_fields=code_v2,text_uthmani&per_page=50&page={batch}", target)
                    response = json.loads(target.read_text())
                    assert response["pagination"]["current_page"] == batch
                    verses.extend(response["verses"])
                    following = response["pagination"]["next_page"]
                    if following is None:
                        break
                    assert following == batch + 1 and batch < 20
                    batch = following
                assert len(verses) == response["pagination"]["total_records"]
                response["verses"] = verses
                response["pagination"].update(current_page=1, total_pages=1, next_page=None)
                target.write_text(json.dumps(response, ensure_ascii=False))
    download(f"https://verses.quran.foundation/fonts/quran/hafs/v2/ttf/p{page}.ttf", output / f"p{page}.ttf")
    return page


with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    futures = [pool.submit(fetch, page) for page in pages]
    for future in concurrent.futures.as_completed(futures):
        print(f"Downloaded page {future.result()}", flush=True)
