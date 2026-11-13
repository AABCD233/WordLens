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

MORPH_SRC = "https://morphemelexicon.com/data/index.json"
CITYLEX = ("https://raw.githubusercontent.com/connoryang331/morphoneme/main/"
           "data/citylex-2026-06-15_morphology_segmention.tsv")
CITYLEX_MIRROR = "https://gh-proxy.org/" + CITYLEX
OUT = os.path.join(os.path.dirname(__file__), "../../app/src/main/assets/morph.db")
DICT = os.path.join(os.path.dirname(__file__), "../../app/src/main/assets/dict.db")


def fetch():
    print("下载索引…")
    req = urllib.request.Request(MORPH_SRC, headers={"User-Agent": "WordLens-build/1.0"})
    return json.loads(urllib.request.urlopen(req, timeout=180).read().decode("utf8"))


def fetch_citylex():
    """CityLex 词素切分；直连不通就走镜像"""
    for u in (CITYLEX, CITYLEX_MIRROR):
        try:
            req = urllib.request.Request(u, headers={"User-Agent": "WordLens-build/1.0"})
            return urllib.request.urlopen(req, timeout=180).read().decode("utf8")
        except Exception as e:
            print("  镜像失败:", type(e).__name__)
    raise SystemExit("CityLex 下载失败")


def parse_seg(seg):
    """<un<{believe}>able>  →  (['un'], ['believe'], ['able'])
       {ab--duct}>ion>     →  ([], ['ab','duct'], ['ion'])"""
    seg = (seg or "").strip()
    if "{" not in seg or "}" not in seg:
        return None
    i, j = seg.index("{"), seg.index("}")
    pre = [p for p in seg[:i].split("<") if p.strip()]
    inner = [p for p in seg[i + 1:j].split("--") if p.strip()]
    suf = [p for p in seg[j + 1:].split(">") if p.strip()]
    if not inner:
        return None
    prefixes = list(pre)
    parts = list(inner)
    if len(parts) > 1:
        prefixes.append(parts[0])
        parts = parts[1:]
    return prefixes, parts, suf


def main():
    data = fetch()
    citylex = fetch_citylex()
    out = os.path.abspath(OUT)
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.executescript("""
        CREATE TABLE family (word TEXT, lemma TEXT, inflection INTEGER, in_dict INTEGER DEFAULT 1);
        CREATE INDEX i_fam_lemma ON family(lemma);
        CREATE TABLE seg (word TEXT PRIMARY KEY, prefix TEXT, root TEXT, suffix TEXT);
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
        rows.append((w, lemma, 1 if p[3].strip() == "1" else 0, 1 if (not known or w in known) else 0))
    db.executemany("INSERT INTO family VALUES (?,?,?,?)", rows)

    # 词素切分只收 CityLex（umLabeller 有实测错误，整源弃用）
    seg_rows = []
    seen = set()
    for line in citylex.split("\n")[1:]:
        p = line.split("\t")
        if len(p) < 3:
            continue
        w = p[1].strip().lower()
        if not w or w in seen:
            continue
        if known and w not in known:
            continue
        parsed = parse_seg(p[2])
        if not parsed:
            continue
        prefixes, roots, suffixes = parsed
        # 全部词素都是同一个词本身（{aaron} 这种）没有拆解价值，跳过
        if not prefixes and not suffixes and len(roots) == 1 and roots[0].lower() == w:
            continue
        seen.add(w)
        seg_rows.append((
            w,
            "--".join(prefixes).lower(),
            "--".join(roots).lower(),
            "--".join(suffixes).lower(),
        ))
    db.executemany("INSERT INTO seg VALUES (?,?,?,?)", seg_rows)

    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"family {len(rows)} 条 / seg {len(seg_rows)} 条")
    print(f"输出 {out}  {os.path.getsize(out)/1024/1024:.1f} MB")


if __name__ == "__main__":
    main()
