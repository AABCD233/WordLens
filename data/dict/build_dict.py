#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 ECDICT（https://github.com/skywind3000/ECDICT，MIT）生成 WordLens 的离线英汉词典。

产物：app/src/main/assets/dict.db
表结构（App 端按列名读取，勿改列名）：
  dict(id INTEGER PRIMARY KEY AUTOINCREMENT,
       word TEXT NOT NULL, phonetic TEXT, translation TEXT, definition TEXT,
       tag TEXT, exchange TEXT, collins INTEGER, oxford INTEGER, frq INTEGER, bnc INTEGER)
  + UNIQUE INDEX idx_dict_word(word)

用法：
  python3 build_dict.py                 # 缺 csv 时自动经 gh-proxy 镜像下载
  python3 build_dict.py --csv 本地.csv   # 用本地数据
"""
import argparse
import csv
import json
import os
import sqlite3
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSET_DIR = os.path.join(ROOT, "app", "src", "main", "assets")
OUT_DB = os.path.join(ASSET_DIR, "dict.db")
BUILD_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "build")
CSV_URL = "https://gh-proxy.com/https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv"

MAX_DB_BYTES = 40 * 1024 * 1024  # App 里词典库的体积上限

ALLOWED_CHARS = set("abcdefghijklmnopqrstuvwxyz-' ")


def ensure_csv(path: str) -> str:
    if path and os.path.exists(path):
        return path
    os.makedirs(BUILD_DIR, exist_ok=True)
    dest = os.path.join(BUILD_DIR, "ecdict.csv")
    if os.path.exists(dest) and os.path.getsize(dest) > 1_000_000:
        return dest
    print(f"[i] 下载 ecdict.csv -> {dest}")
    req = urllib.request.Request(CSV_URL, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=180) as resp, open(dest, "wb") as f:
        total, chunk = 0, 1 << 20
        while True:
            data = resp.read(chunk)
            if not data:
                break
            f.write(data)
            total += len(data)
            if total % (10 * chunk) == 0:
                print(f"    {total/1e6:.1f} MB")
    print(f"[i] 下载完成 {total/1e6:.1f} MB")
    return dest


def clean_translation(text: str) -> str:
    if not text:
        return ""
    # ECDICT 里混用真换行和字面 \n，统一还原成真换行
    text = text.replace("\\r", "").replace("\\n", "\n").replace("\r", "")
    lines = [line.strip() for line in text.split("\n")]
    return "\n".join([line for line in lines if line])


def score(row) -> int:
    """信息更全的行优先保留（同词去重时用）。"""
    s = 0
    if row["oxford"] == 1:
        s += 100_000
    if row["collins"] > 0:
        s += 10_000 * min(row["collins"], 5)
    if row["frq"] > 0:
        s += max(0, 10_000 - row["frq"])
    s += min(len(row["translation"]), 600) * 3
    s += min(len(row["definition"]), 400)
    if row["exchange"]:
        s += 500
    if row["tag"]:
        s += 300
    return s


def iter_rows(csv_path: str, require_signal: bool = True):
    dropped = 0
    with open(csv_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for raw in reader:
            word = (raw.get("word") or "").strip()
            translation = clean_translation(raw.get("translation") or "")
            if not word or not translation:
                dropped += 1
                continue
            w = word.lower()
            if not set(w) <= ALLOWED_CHARS or len(w) > 40:
                dropped += 1
                continue
            collins = int(raw.get("collins") or 0)
            oxford = 1 if (raw.get("oxford") or "").strip() == "1" else 0
            frq = int(raw.get("frq") or 0)
            tag = (raw.get("tag") or "").strip().lower()
            exchange = (raw.get("exchange") or "").strip()
            bnc = int(raw.get("bnc") or 0)
            # 核心包过滤长尾噪音：至少要有一种「权威信号」；全量包不过滤
            if require_signal and not (oxford or collins > 0 or frq > 0 or exchange or tag):
                dropped += 1
                continue
            row = {
                "word": w,
                "phonetic": (raw.get("phonetic") or "").strip(),
                "definition": (raw.get("definition") or "").strip(),
                "translation": translation,
                "tag": tag,
                "exchange": exchange,
                "collins": collins,
                "oxford": oxford,
                "frq": frq,
                "bnc": bnc,
            }
            yield row, score(row)
    if dropped:
        print(f"[i] 过滤掉 {dropped} 条（空释义 / 非纯英文拼写 / 无权威信号）")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", default=None, help="本地 ecdict.csv 路径")
    ap.add_argument("--profile", default="core", choices=["core", "full"],
                    help="core=内置精简包（默认）；full=全量扩展包，供 App 内下载")
    ap.add_argument("--out", default=None, help="输出路径，默认 core 进 assets，full 进 build/")
    args = ap.parse_args()
    csv_path = ensure_csv(args.csv)
    full = args.profile == "full"
    out_db = args.out or (os.path.join(BUILD_DIR, "dict-full.db") if full else OUT_DB)
    os.makedirs(os.path.dirname(out_db), exist_ok=True)

    os.makedirs(ASSET_DIR, exist_ok=True)
    if os.path.exists(out_db):
        os.remove(out_db)

    conn = sqlite3.connect(out_db)
    conn.executescript(
        """
        PRAGMA journal_mode=DELETE;
        PRAGMA synchronous=OFF;
        CREATE TABLE staging(
          word TEXT NOT NULL, phonetic TEXT, translation TEXT, definition TEXT,
          tag TEXT, exchange TEXT, collins INTEGER, oxford INTEGER, frq INTEGER, bnc INTEGER,
          score INTEGER NOT NULL
        );
        CREATE INDEX idx_staging_word_score ON staging(word, score DESC);
        CREATE TABLE dict(
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          word TEXT NOT NULL,
          phonetic TEXT,
          translation TEXT,
          definition TEXT,
          tag TEXT,
          exchange TEXT,
          collins INTEGER,
          oxford INTEGER,
          frq INTEGER,
          bnc INTEGER
        );
        CREATE UNIQUE INDEX idx_dict_word ON dict(word);
        """
    )

    insert_sql = (
        "INSERT INTO staging(word,phonetic,translation,definition,tag,exchange,"
        "collins,oxford,frq,bnc,score) VALUES(?,?,?,?,?,?,?,?,?,?,?)"
    )
    batch, staged = [], 0
    for row, sc in iter_rows(csv_path, require_signal=not full):
        batch.append((
            row["word"], row["phonetic"], row["translation"], row["definition"],
            row["tag"], row["exchange"], row["collins"], row["oxford"], row["frq"], row["bnc"], sc,
        ))
        if len(batch) >= 5000:
            conn.executemany(insert_sql, batch)
            staged += len(batch)
            batch.clear()
    if batch:
        conn.executemany(insert_sql, batch)
        staged += len(batch)
        batch.clear()
    conn.commit()
    print(f"[i] staging 载入 {staged} 行")

    # 同词去重：保留 score 最高的那条
    conn.execute(
        """
        INSERT INTO dict(word,phonetic,translation,definition,tag,exchange,collins,oxford,frq,bnc)
        SELECT word, phonetic, translation, definition, tag, exchange, collins, oxford, frq, bnc
        FROM staging s
        WHERE s.rowid = (
          SELECT s2.rowid FROM staging s2
          WHERE s2.word = s.word
          ORDER BY s2.score DESC, s2.rowid ASC
          LIMIT 1
        )
        """
    )
    conn.execute("DROP TABLE staging")
    conn.commit()
    conn.execute("VACUUM")
    conn.commit()

    def shrink(threshold: int) -> int:
        """砍长尾：只有词频信号、且名次比阈值更靠后的行删掉。"""
        conn.execute(
            "DELETE FROM dict WHERE oxford=0 AND collins=0 AND frq>? AND (tag='' OR tag IS NULL)",
            (threshold,),
        )
        conn.commit()
        conn.execute("VACUUM")
        conn.commit()
        return os.path.getsize(out_db)

    size = os.path.getsize(out_db)
    thresholds = [12000, 8000, 5000, 3000, 2000, 1000]
    ti = 0
    while not full and size > MAX_DB_BYTES and ti < len(thresholds):
        before = conn.execute("SELECT COUNT(1) FROM dict").fetchone()[0]
        size = shrink(thresholds[ti])
        after = conn.execute("SELECT COUNT(1) FROM dict").fetchone()[0]
        print(f"[i] 收紧 frq>{thresholds[ti]}: {before} -> {after} 行, {size/1e6:.1f} MB")
        ti += 1

    conn.execute("PRAGMA user_version=1")
    conn.commit()

    # ---- 自检 ----
    total = conn.execute("SELECT COUNT(1) FROM dict").fetchone()[0]
    non_lower = conn.execute("SELECT COUNT(1) FROM dict WHERE word <> lower(word)").fetchone()[0]
    empty_tr = conn.execute("SELECT COUNT(1) FROM dict WHERE translation=''").fetchone()[0]
    integrity = conn.execute("PRAGMA integrity_check").fetchone()[0]
    uv = conn.execute("PRAGMA user_version").fetchone()[0]
    mode = conn.execute("PRAGMA journal_mode").fetchone()[0]

    samples = {}
    for w in ("apple", "knowledge", "run", "the"):
        row = conn.execute("SELECT phonetic, translation FROM dict WHERE word=?", (w,)).fetchone()
        samples[w] = {
            "phonetic": row[0] if row else None,
            "translation": (row[1][:120] if row and row[1] else None),
        }

    report = {
        "profile": args.profile,
        "out": os.path.basename(out_db),
        "rows": total,
        "size_mb": round(size / 1e6, 2),
        "size_bytes": size,
        "non_lowercase": non_lower,
        "empty_translation": empty_tr,
        "integrity_check": integrity,
        "journal_mode": mode,
        "user_version": uv,
        "samples": samples,
    }
    os.makedirs(BUILD_DIR, exist_ok=True)
    stats_name = "build_stats_full.json" if full else "build_stats.json"
    with open(os.path.join(BUILD_DIR, stats_name), "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    print(json.dumps(report, ensure_ascii=False, indent=2))
    conn.close()
    print(f"[ok] 产物: {out_db}  ({size/1e6:.1f} MB, {total} 词)")
    return 0 if integrity == "ok" and non_lower == 0 and empty_tr == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
