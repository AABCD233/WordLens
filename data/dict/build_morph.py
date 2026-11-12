#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成词根/词族小库 assets/morph.db。

数据源：morphemelexicon.com/data/index.json（公开数据集，非商用引用并致谢）
  words   TSV: word \t lemma \t freq \t inflection \t 中文释义
  roots   [root, en, count, origin, kind, score, zh]
  affixes [affix, type(prefix|suffix), en, count, zh]

用法: python3 data/dict/build_morph.py
"""
import json, os, sqlite3, sys, urllib.request

SRC = "https://morphemelexicon.com/data/index.json"
OUT = os.path.join(os.path.dirname(__file__), "../../app/src/main/assets/morph.db")
DICT = os.path.join(os.path.dirname(__file__), "../../app/src/main/assets/dict.db")


def fetch():
    print("下载索引…")
    req = urllib.request.Request(SRC, headers={"User-Agent": "WordLens-build/1.0"})
    return json.loads(urllib.request.urlopen(req, timeout=180).read().decode("utf8"))


def main():
    data = fetch()
    out = os.path.abspath(OUT)
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.executescript("""
        CREATE TABLE family (word TEXT, lemma TEXT, inflection INTEGER);
        CREATE INDEX i_fam_lemma ON family(lemma);
        CREATE TABLE root (root TEXT, kind TEXT, en TEXT, zh TEXT, freq INTEGER);
        CREATE INDEX i_root ON root(root);
        CREATE TABLE affix (affix TEXT, type TEXT, zh TEXT, freq INTEGER);
        PRAGMA journal_mode=DELETE;
    """)

    # 我们词典里有哪些词：词根/词族只保留能对上的，省体积
    known = set()
    if os.path.exists(os.path.abspath(DICT)):
        d = sqlite3.connect(os.path.abspath(DICT))
        known = {r[0].lower() for r in d.execute("SELECT word FROM dict")}
        d.close()
    print("内置词典词数:", len(known))

    rows = []
    for line in data["words"].split("\n"):
        p = line.split("\t")
        if len(p) < 4:
            continue
        w, lemma = p[0].strip().lower(), p[1].strip().lower()
        if not w or not lemma:
            continue
        if known and w not in known:
            continue
        rows.append((w, lemma, 1 if p[3].strip() == "1" else 0))
    db.executemany("INSERT INTO family VALUES (?,?,?)", rows)

    roots = []
    for r in data["roots"]:
        root, en, cnt, _origin, kind, _score, zh = (list(r) + [""] * 7)[:7]
        if not root or cnt < 5:
            continue
        roots.append((root.lower(), kind or "", en or "", zh or "", int(cnt)))
    db.executemany("INSERT INTO root VALUES (?,?,?,?,?)", roots)

    affixes = []
    for a in data["affixes"]:
        aff, typ, _en, cnt, zh = (list(a) + [""] * 5)[:5]
        if not aff or not typ or not zh:
            continue
        affixes.append((aff.lower(), typ, zh, int(cnt)))
    db.executemany("INSERT INTO affix VALUES (?,?,?,?)", affixes)

    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"family {len(rows)} 条 / root {len(roots)} 条 / affix {len(affixes)} 条")
    print(f"输出 {out}  {os.path.getsize(out)/1024/1024:.1f} MB")


if __name__ == "__main__":
    main()
