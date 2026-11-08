"""Build six independent CET word books and their exact per-book order.

The generated union in words.tsv is an implementation detail.  book-orders.tsv
keeps every selectable book separate, including its own order and membership.

Example:
  python tools/prepare_branded_books.py \
    --dictionary-root work/book-sources/dictionary-data \
    --cet4-flash work/book-sources/branded/cet4-flash.xlsx \
    --cet6-flash work/book-sources/branded/cet6-flash.xlsx \
    --ecdict work/watch-build/ecdict.csv
"""
import argparse
import collections
import csv
import hashlib
import json
import pathlib
import re

import openpyxl


BOOKS = (
    dict(key="cet4_star", bit=1, level=4, brand="星火", title="四级词汇必背乱序版",
         edition="2014年3月第1版", dictionary_id="5aa73cd6b350dc083cb93fe5"),
    dict(key="cet4_new_oriental", bit=2, level=4, brand="新东方", title="四级词汇正序版",
         edition="2013年11月第1版", dictionary_id="38b3eff8baf56627478ec76a"),
    dict(key="cet4_flash", bit=4, level=4, brand="闪过", title="四级词汇闪过",
         edition="公开整理词序", spreadsheet="cet4"),
    dict(key="cet6_star", bit=8, level=6, brand="星火", title="六级巧记速记乱序版",
         edition="2009年7月第1版", dictionary_id="c45147dee729311ef5b5c300"),
    dict(key="cet6_new_oriental", bit=16, level=6, brand="新东方", title="六级词汇正序版（红宝书）",
         edition="2013年8月第1版", dictionary_id="7647966b7343c29048673252"),
    dict(key="cet6_flash", bit=32, level=6, brand="闪过", title="六级词汇闪过乱序版",
         edition="公开整理词序", spreadsheet="cet6"),
)


def normalize(value):
    value = str(value or "").strip().lower().replace("’", "'")
    # Two source workbooks contain replacement characters in otherwise clear
    # French loanwords.  Keep the intended spelling rather than a broken ID.
    return {"r�sum�": "résumé", "clich�": "cliché"}.get(value, value)


def valid_headword(value):
    if not value or len(value) > 48 or not any(ch.isalpha() for ch in value):
        return False
    return all(ch.isalpha() or ch in " -'." for ch in value)


def brief(raw):
    lines = str(raw or "").replace("\\n", "\n").splitlines()
    useful = [x.strip() for x in lines if x.strip() and not x.lstrip().startswith("[")]
    if not useful:
        useful = lines
    pos, pieces = "", []
    for line in useful:
        match = re.match(r"^([a-z./ &]+\.)\s*", line, re.I)
        if match and not pos:
            pos = match.group(1).replace(" ", "").lower()
        line = re.sub(r"^[a-z./ &]+\.\s*", "", line, flags=re.I)
        line = re.sub(r"\([^)]*\)|（[^）]*）|\[[^]]*\]|【[^】]*】", "", line)
        line = line.replace("…", "").replace("...", "").strip(" =；;，,")
        for piece in re.split(r"[；;，,\n]", line):
            piece = piece.strip(" .。:：")
            if not re.search(r"[\u4e00-\u9fff]", piece) or len(piece) > 18 or piece in pieces:
                continue
            if sum(map(len, pieces)) + len(piece) + len(pieces) <= 24:
                pieces.append(piece)
            if len(pieces) >= 3:
                break
        if len(pieces) >= 2:
            break
    aliases = {"a.": "adj.", "ad.": "adv.", "vt.": "v.", "vi.": "v.", "v.t.": "v.", "v.i.": "v."}
    return "；".join(pieces), aliases.get(pos, pos or "词")


def load_dictionary_books(root):
    by_id = {}
    with (root / "word.csv").open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle, delimiter=">"):
            by_id[row["vc_id"]] = row

    wanted = {book["dictionary_id"]: book["key"] for book in BOOKS if "dictionary_id" in book}
    orders = {book["key"]: [] for book in BOOKS if "dictionary_id" in book}
    relation = root / "relations" / "relation_book_word.csv"
    with relation.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle, delimiter=">"):
            key = wanted.get(row["bv_book_id"])
            if key:
                orders[key].append((float(row["bv_order"]), row["bv_voc_id"]))

    result, metadata = {}, {}
    for key, rows in orders.items():
        rows.sort()
        seen, words = set(), []
        for _, voc_id in rows:
            item = by_id[voc_id]
            word = normalize(item["vc_vocabulary"])
            if not valid_headword(word) or word in seen:
                continue
            seen.add(word)
            words.append(word)
            metadata.setdefault(word, {}).update(
                ipa=item["vc_phonetic_uk"].strip("[] ") or item["vc_phonetic_us"].strip("[] ")
            )
        result[key] = words

    translations = {}
    with (root / "word_translation.csv").open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            word = normalize(row["word"])
            if word and word not in translations:
                translations[word] = row["translation"]
    for word, raw in translations.items():
        if word in metadata:
            metadata[word]["translation"] = raw
    return result, metadata


def load_spreadsheet(path):
    sheet = openpyxl.load_workbook(path, read_only=True, data_only=True).active
    words, metadata, seen = [], {}, set()
    for row in sheet.iter_rows(min_row=2, values_only=True):
        word = normalize(row[0])
        if not valid_headword(word) or word in seen:
            continue
        seen.add(word)
        words.append(word)
        metadata[word] = {
            "ipa": str(row[1] or row[2] or "").strip("[] "),
            "translation": str(row[3] or ""),
        }
    return words, metadata


def load_ecdict(path, wanted):
    result = {}
    with path.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            word = normalize(row["word"])
            if word in wanted and word not in result:
                result[word] = row
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dictionary-root", required=True, type=pathlib.Path)
    parser.add_argument("--cet4-flash", required=True, type=pathlib.Path)
    parser.add_argument("--cet6-flash", required=True, type=pathlib.Path)
    parser.add_argument("--ecdict", required=True, type=pathlib.Path)
    args = parser.parse_args()
    destination = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/assets"

    orders, source_metadata = load_dictionary_books(args.dictionary_root)
    for level, path in ((4, args.cet4_flash), (6, args.cet6_flash)):
        words, metadata = load_spreadsheet(path)
        orders[f"cet{level}_flash"] = words
        for word, values in metadata.items():
            source_metadata.setdefault(word, {}).update(values)

    all_words = set().union(*map(set, orders.values()))
    ecdict = load_ecdict(args.ecdict, all_words)
    overrides = json.loads((pathlib.Path(__file__).with_name("meaning-overrides.json")).read_text(encoding="utf-8"))

    masks = collections.defaultdict(int)
    for book in BOOKS:
        for word in orders[book["key"]]:
            masks[word] |= book["bit"]

    old_media_path = destination / "word-media.json"
    old_media = json.loads(old_media_path.read_text(encoding="utf-8")) if old_media_path.exists() else {}
    media, records, missing = {}, [], []
    for word in sorted(all_words):
        meta = source_metadata.get(word, {})
        row = ecdict.get(word)
        raw = row["translation"] if row and row.get("translation") else meta.get("translation", "")
        meaning, pos = brief(raw)
        if word in overrides:
            meaning, pos = overrides[word]
        while len(meaning) > 24 and "；" in meaning:
            meaning = meaning.rsplit("；", 1)[0]
        if not meaning:
            missing.append(word)
            continue
        bank = (1 if masks[word] & 7 else 0) | (2 if masks[word] & 56 else 0)
        previous = old_media.get(word, {})
        scene = previous.get("scene", "")
        hint = ""
        entry = dict(previous)
        ipa = (row.get("phonetic", "").strip() if row else "") or meta.get("ipa", "")
        if ipa and not entry.get("ipa"):
            entry["ipa"] = ipa.strip("[] /")
        if entry:
            media[word] = entry
        records.append((word, meaning, pos, bank, scene, hint, masks[word]))

    if missing:
        raise ValueError("Missing a short Chinese meaning for: " + ", ".join(missing[:30]))

    with (destination / "words.tsv").open("w", encoding="utf-8", newline="\n") as handle:
        for record in records:
            handle.write("\t".join(map(str, record)) + "\n")
    with (destination / "book-orders.tsv").open("w", encoding="utf-8", newline="\n") as handle:
        for book in BOOKS:
            for word in orders[book["key"]]:
                handle.write(f"{book['bit']}\t{word}\n")
    old_media_path.write_text(json.dumps(media, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    report_books = []
    for book in BOOKS:
        report_books.append({
            **{k: book[k] for k in ("key", "bit", "level", "brand", "title", "edition")},
            "headwords": len(orders[book["key"]]),
            "first": orders[book["key"]][0],
            "last": orders[book["key"]][-1],
        })
    report = {
        "status": "six_separate_branded_books",
        "union_headwords": len(records),
        "books": report_books,
        "sources": [
            {"name": "DictionaryData", "url": "https://github.com/LinXueyuanStdio/DictionaryData", "license": "Apache-2.0"},
            {"name": "English Vocabulary Vault", "url": "https://github.com/lilinji/English", "license": "repository non-commercial study notice"},
            {"name": "ECDICT", "url": "https://github.com/skywind3000/ECDICT", "license": "MIT"},
        ],
        "sha256": {
            "dictionary_book": hashlib.sha256((args.dictionary_root / "book.csv").read_bytes()).hexdigest(),
            "dictionary_relation": hashlib.sha256((args.dictionary_root / "relations/relation_book_word.csv").read_bytes()).hexdigest(),
            "cet4_flash": hashlib.sha256(args.cet4_flash.read_bytes()).hexdigest(),
            "cet6_flash": hashlib.sha256(args.cet6_flash.read_bytes()).hexdigest(),
            "ecdict": hashlib.sha256(args.ecdict.read_bytes()).hexdigest(),
        },
        "note": "六本书保留独立成员和独立顺序；words.tsv 仅为节省安装空间的去重存储。重合词共用个人记忆进度。释义优先采用 ECDICT 短释义，词书用于成员和词序。",
    }
    (destination / "vocabulary-info.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
