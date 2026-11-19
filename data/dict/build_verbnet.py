#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 VerbNet 的动词句法帧抽成小表，追加进 assets/morph.db 的 verb_frame。

用法: python3 data/dict/build_verbnet.py <verbnet 解压目录>
数据来源 cu-clear/verbnet（VerbNet 3.4），许可允许任意用途，需保留版权与引用。
"""
import glob, os, re, sqlite3, sys
import xml.etree.ElementTree as ET

MORPH = os.path.join(os.path.dirname(__file__), "../../app/src/main/assets/morph.db")

# 句法帧里的成分 → 人话
TOKEN_ZH = [
    ("S_ING", "doing sth"),
    ("S_INF", "to do sth"),
    ("PP.", "介词短语"),
    ("NP.", "宾语"),
    ("ADJP", "形容词"),
    ("ADVP", "副词"),
    ("ADV", "副词"),
    ("PRT", "小品词"),
    ("PART", "小品词"),
    ("NP", "宾语"),
]


def template_of(frame: str) -> str:
    toks = frame.split()
    out = []
    for t in toks:
        if t == "NP" and not out:
            continue            # 主语不写在模板里
        if t == "V":
            out.append("V")
            continue
        for key, zh in TOKEN_ZH:
            if t.startswith(key):
                out.append(zh)
                break
        else:
            out.append(t)
    return " + ".join(out)


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "/tmp/vn"
    files = glob.glob(os.path.join(root, "**", "verbnet3.4", "*.xml"), recursive=True)
    if not files:
        files = glob.glob(os.path.join(root, "**", "*.xml"), recursive=True)
    print("XML 文件:", len(files))

    rows = []
    for f in files:
        try:
            t = ET.parse(f).getroot()
        except Exception:
            continue
        members = [m.get("name") for m in t.iter("MEMBER") if m.get("name")]
        frames = []
        for fr in t.iter("FRAME"):
            desc = fr.find("DESCRIPTION")
            frame = desc.get("primary") if desc is not None else ""
            ex = fr.find(".//EXAMPLE")
            example = (ex.text or "").strip() if ex is not None and ex.text else ""
            if frame:
                frames.append((frame, example))
        # 子类里的帧也算
        for sub in t.iter("VNSUBCLASS"):
            for fr in sub.iter("FRAME"):
                desc = fr.find("DESCRIPTION")
                frame = desc.get("primary") if desc is not None else ""
                ex = fr.find(".//EXAMPLE")
                example = (ex.text or "").strip() if ex is not None and ex.text else ""
                if frame:
                    frames.append((frame, example))
        seen = set()
        for m in members:
            w = m.lower().strip()
            if not w or w in seen:
                continue
            seen.add(w)
            for frame, example in frames[:4]:
                # 例句多数是同类原型动词的句子（I helped him. 出现在 assist 名下），
                # 只有真含本词时才留着，否则只给句型。
                ex = example if w in example.lower() else ""
                rows.append((w, template_of(frame), frame, ex))

    # 去重（同词同帧只留一条）
    uniq = {}
    for w, tpl, frame, ex in rows:
        uniq.setdefault((w, frame), (w, tpl, frame, ex))
    rows = list(uniq.values())
    print("动词:", len({r[0] for r in rows}), "| 帧记录:", len(rows))

    out = os.path.abspath(MORPH)
    db = sqlite3.connect(out)
    db.executescript("""
        DROP TABLE IF EXISTS verb_frame;
        CREATE TABLE verb_frame (word TEXT, template TEXT, frame TEXT, example TEXT);
        CREATE INDEX i_vf ON verb_frame(word);
    """)
    db.executemany("INSERT INTO verb_frame VALUES (?,?,?,?)", rows)
    db.execute("PRAGMA user_version = 3")   # 结构版本，App 靠它决定要不要重新拷贝
    db.commit()
    db.execute("VACUUM")
    db.close()
    print("写入", out, f"{os.path.getsize(out)/1024/1024:.1f} MB")


if __name__ == "__main__":
    main()
