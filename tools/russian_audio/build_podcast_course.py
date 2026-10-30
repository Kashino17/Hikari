#!/usr/bin/env python3
"""Build and validate the local RusslandJournal podcast course asset."""

import argparse
import concurrent.futures
import html
import json
import pathlib
import re
import subprocess
import sys
import urllib.request
from html.parser import HTMLParser

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSET = ROOT / "android/app/src/main/assets/russian/podcast.json"
MAIN_COURSE = ROOT / "android/app/src/main/assets/russian/course.json"
INDEX_URL = "https://www.russlandjournal.de/russisch-lernen/podcast/"
CHANNEL_URL = "https://www.youtube.com/@RussischLernenPodcast/videos"
USER_AGENT = "HikariRussianCourseBuilder/1.0"


class PageText(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []
        self.skip = 0

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "noscript"):
            self.skip += 1
        elif not self.skip and tag in ("p", "li", "h1", "h2", "h3", "h4", "br", "tr", "div"):
            self.parts.append("\n")

    def handle_endtag(self, tag):
        if tag in ("script", "style", "noscript"):
            self.skip = max(0, self.skip - 1)
        elif not self.skip and tag in ("p", "li", "h1", "h2", "h3", "h4", "tr", "div"):
            self.parts.append("\n")

    def handle_data(self, data):
        if not self.skip:
            self.parts.append(data)

    def lines(self):
        text = html.unescape("".join(self.parts)).replace("\xa0", " ")
        return [re.sub(r"\s+", " ", line).strip() for line in text.splitlines() if line.strip()]


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=45) as response:
        return response.read().decode("utf-8")


def lesson_links():
    links = []
    for link in re.findall(r'href=["\']([^"\']+)["\']', fetch(INDEX_URL)):
        match = re.search(r"/russisch-lernen/podcast/lektion-(\d+)-[^/]+/?$", link)
        if match:
            number = int(match.group(1))
            canonical = link if link.startswith("http") else "https://www.russlandjournal.de" + link
            links.append((number, canonical))
    return dict(sorted(set(links)))


def video_ids():
    result = subprocess.run(
        ["yt-dlp", CHANNEL_URL, "--flat-playlist", "--dump-single-json", "--no-warnings"],
        check=True,
        capture_output=True,
        text=True,
    )
    videos = {}
    for entry in json.loads(result.stdout)["entries"]:
        match = re.match(r"(\d{3})\s", entry.get("title", ""))
        if match:
            videos[int(match.group(1))] = entry["id"]
    return videos


def clean_item(line, number, position):
    match = re.match(r"^(.+?)\s+[-–]\s+(.+)$", line)
    if not match:
        return None
    ru, de = match.groups()
    ru = re.sub(r"\s*\[[^]]*]", "", ru)
    ru = re.sub(r"\s*\([^)]*[A-Za-z][^)]*\)\.?", "", ru)
    ru = re.sub(r"\s+", " ", ru).strip(" -–—\t")
    de = re.sub(r"\s+", " ", de).strip()
    if not re.search(r"[А-Яа-яЁё]", ru) or not re.search(r"[A-Za-zÄÖÜäöüß]", de):
        return None
    if re.search(r"[A-Za-z]", ru) or re.search(r"[А-Яа-яЁё]", de):
        return None
    if re.fullmatch(r"(?:Siehe auch )?Lektion\s+\d+", de, re.I):
        return None
    if len(ru) < 2 or len(de) < 2 or len(ru) > 240 or len(de) > 280:
        return None
    return {
        "id": f"rj{number:03d}_{position:02d}",
        "ru": ru,
        "de": de,
    }


NOISE_PREFIXES = (
    "PDF-Buch", "Alle Texte", "Querverweise", "Wortverzeichnis", "Ausdrucken:",
    "Mehr Infos", "Anzeige", "✏️", "❗️", "Übung zu", "Dieser Podcast",
    "Weitere Hörproben", "Audio-Player", "Russische Schrift und Aussprache",
)


def useful_note(line, item_lines):
    if line in item_lines or len(line) < 65 or line.startswith(NOISE_PREFIXES):
        return False
    if clean_item(line, 0, 0) is not None or re.match(r"^[^-–]*[А-Яа-яЁё][^-–]*\s+[-–]\s+", line):
        return False
    if re.match(r"^(?:Lektion|Russische (?:Vokabeln|Grammatik)|NÄCHSTE|VORHERIGE)", line, re.I):
        return False
    if "http" in line or "RusslandJournal+" in line:
        return False
    return True


def parse_lesson(number, url, video_id):
    source = fetch(url)
    parser = PageText()
    parser.feed(source)
    lines = parser.lines()
    heading = re.search(r"<h1[^>]*>(.*?)</h1>", source, re.S | re.I)
    title = "Lektion " + str(number)
    if heading:
        title = re.sub(r"<[^>]+>", "", html.unescape(heading.group(1)))
        title = re.sub(r"^Lektion\s+\d+:\s*", "", title).strip()
    start = next((i for i, line in enumerate(lines) if re.match(rf"^Урок\s+{number}\b", line)), None)
    if start is None:
        raise ValueError(f"Lektion {number}: Kursanfang nicht gefunden")
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith(("Download-Möglichkeiten", "NÄCHSTE LEKTION", "VORHERIGE LEKTION"))), len(lines))
    content = lines[start + 1:end]
    items = []
    item_lines = set()
    seen = set()
    for line in content:
        candidate = clean_item(line, number, len(items) + 1)
        if not candidate:
            continue
        key = (candidate["ru"].lower(), candidate["de"].lower())
        if key in seen:
            continue
        seen.add(key)
        items.append(candidate)
        item_lines.add(line)
        if len(items) == 12:
            break
    notes = []
    for line in content:
        if useful_note(line, item_lines):
            notes.append(line[:650])
        if len(notes) == 5:
            break
    summary = (
        f"Diese Lektion vertieft „{title}“. Höre die Folge zuerst in Ruhe, "
        "lies anschließend die Erklärungen und wende die Beispiele direkt in den Übungen an."
    )
    return {
        "day": number,
        "title": title,
        "goal": title,
        "sound": {"title": "", "text": "", "examples": []},
        "items": items,
        "dialog": {"id": f"rj{number:03d}_dialog", "title": "", "scene": "", "lines": []},
        "media": {
            "videoId": video_id,
            "sourceUrl": url,
            "summary": summary,
            "notes": notes,
        },
    }


def validate(course):
    errors = []
    days = course.get("days", [])
    if [d.get("day") for d in days] != list(range(1, 101)):
        errors.append("Lektionen müssen lückenlos von 1 bis 100 reichen")
    ids = set()
    videos = set()
    main_ids = {
        item["id"]
        for day in json.loads(MAIN_COURSE.read_text())["days"]
        for item in day["items"]
    }
    for day in days:
        number = day.get("day")
        media = day.get("media", {})
        video = media.get("videoId", "")
        if not re.fullmatch(r"[A-Za-z0-9_-]{11}", video):
            errors.append(f"Lektion {number}: ungültige Video-ID {video!r}")
        if video in videos:
            errors.append(f"Lektion {number}: doppelte Video-ID {video}")
        videos.add(video)
        if not media.get("sourceUrl", "").startswith("https://www.russlandjournal.de/"):
            errors.append(f"Lektion {number}: Quellenlink fehlt")
        if not media.get("summary") or not media.get("notes"):
            errors.append(f"Lektion {number}: Zusammenfassung oder Notizen fehlen")
        if len(day.get("items", [])) < 6:
            errors.append(f"Lektion {number}: nur {len(day.get('items', []))} Lernphrasen")
        for item in day.get("items", []):
            item_id = item.get("id", "")
            if item_id in ids or item_id in main_ids:
                errors.append(f"Lektion {number}: doppelte ID {item_id}")
            ids.add(item_id)
            if not re.search(r"[А-Яа-яЁё]", item.get("ru", "")):
                errors.append(f"{item_id}: russischer Text fehlt")
            if re.search(r"[A-Za-z]", item.get("ru", "")):
                errors.append(f"{item_id}: lateinischer Rest im russischen Text")
            if not item.get("de", "").strip():
                errors.append(f"{item_id}: deutsche Übersetzung fehlt")
            if re.search(r"[А-Яа-яЁё]", item.get("de", "")):
                errors.append(f"{item_id}: russischer Rest in deutscher Übersetzung")
    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="nur vorhandenes Asset validieren")
    args = parser.parse_args()
    if not args.check:
        links = lesson_links()
        videos = video_ids()
        missing = sorted(set(range(1, 101)) - links.keys() - videos.keys())
        if missing:
            raise SystemExit(f"Fehlende Lektionen/Videos: {missing}")
        def build(number):
            return parse_lesson(number, links[number], videos[number])
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
            days = list(executor.map(build, range(1, 101)))
        course = {"version": 1, "days": days}
        errors = validate(course)
        if errors:
            print("\n".join(errors), file=sys.stderr)
            raise SystemExit(1)
        ASSET.write_text(json.dumps(course, ensure_ascii=False, indent=2) + "\n")
        print(f"{ASSET.relative_to(ROOT)} geschrieben")
    else:
        course = json.loads(ASSET.read_text())
        errors = validate(course)
        if errors:
            print("\n".join(errors), file=sys.stderr)
            raise SystemExit(1)
        print(f"OK: {len(course['days'])} Lektionen, {sum(len(d['items']) for d in course['days'])} Lernphrasen")


if __name__ == "__main__":
    main()
