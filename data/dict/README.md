# WordLens 离线词典 `data/dict/`

这个目录负责生成 APK 里的离线英汉词典 **`app/src/main/assets/dict.db`**（SQLite3，UTF-8，单文件）。

```
data/dict/
├── build_dict.py   脚本（纯 python3 标准库，无第三方依赖）
├── LICENSE.md      数据来源与许可（ECDICT，MIT）
├── README.md       本文件
└── build/          自动生成：下载的 ecdict.csv、build_stats.json
```

## 1. 重新生成

```bash
cd data/dict
python3 build_dict.py                        # 已有 build/ecdict.csv 就直接用，否则自动下载
python3 build_dict.py --csv /path/ecdict.csv # 用指定 csv，完全离线
```

耗时取决于网络：下载约 63 MB（本机实测 ~85 s），入库去重约 1–2 分钟。
磁盘需要 ≥ 200 MB 空闲，内存峰值约 150 MB（逐行流式处理，不整表进内存）。

## 2. 数据源

[ECDICT](https://github.com/skywind3000/ECDICT) 的 `ecdict.csv`（约 77 万条，MIT，见 `LICENSE.md`）。

本机 `raw.githubusercontent.com` 直连被拒，脚本走 `gh-proxy.com` 镜像；
换网络环境时可以直接改脚本顶部的 `CSV_URL`，或者先用别的办法把 csv 下好再 `--csv` 传入。

## 3. 筛选规则

保留同时满足下列条件的行：

1. `translation`（中文释义）非空；
2. `word` 只含 `a-z`、空格、连字符、撇号，长度 ≤ 40（丢掉数字、带变音符号的、短语里的怪字符）；
3. 至少有一个「权威信号」：`oxford=1` / `collins>0` / `frq>0` / `exchange` 非空 / `tag` 非空。

`word` 统一转小写入库；同一个小写词有多行时，按打分保留信息最全的一条
（`oxford` 100000 + `collins` 每星 10000 + 词频名次 10000-名次 + 释义长度×3 + 英文释义长度 + 变形 500 + 标签 300）。

产物超过 40 MB 时，按 `frq` 名次阈值（12000 → 8000 → … → 1000）逐档收紧，
优先砍掉「只有词频信号、名次很靠后」的长尾词，并每次 `VACUUM` 后重新量体积。

## 4. 表结构（App 端按列名读，改列名要同步改 `DictStore.kt`）

```sql
CREATE TABLE dict (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  word TEXT NOT NULL,      -- 全小写
  phonetic TEXT,           -- 音标（不带斜杠）
  translation TEXT,        -- 中文释义，多行，按换行分词性
  definition TEXT,         -- 英文释义
  tag TEXT,                -- zk,gk,cet4,cet6,ky,ielts,toefl,gre...
  exchange TEXT,           -- ECDICT 原生变形串，如 "p:studied|d:studied|i:studying"
  collins INTEGER,         -- 0 或 1..5
  oxford INTEGER,          -- 0/1
  frq INTEGER,             -- 词频名次，越小越高频，0 = 无
  bnc INTEGER              -- BNC 名次，0 = 无
);
CREATE UNIQUE INDEX idx_dict_word ON dict(word);
```

文件属性：`journal_mode=DELETE`（单文件、无 `-wal/-shm`）、`user_version=1`、`VACUUM` 收尾。

## 5. 当前产物（`build/build_stats.json` 同内容）

| 指标 | 值 |
| --- | --- |
| 行数 | **114,247** |
| 文件大小 | **23.67 MB** |
| 数据源 | ECDICT `ecdict.csv`（经 gh-proxy 镜像） |
| 非小写 word / 空 translation | 0 / 0 |
| integrity_check / journal_mode / user_version | ok / delete / 1 |
| apple | `'æpl` · n. 苹果, 家伙 / [医] 苹果 |
| knowledge | `'nɒlidʒ` · n. 知识, 学问, 认识, 知道 |
| run | `rʌn` · n./vi./vt. 跑, 奔跑, 经营… |
| the | `ðә` · art. 那 |

## 6. Android 侧注意

- aapt 默认会压缩 `.db` asset，`AssetFileDescriptor` 拿不到；所以 App 侧用「先试 `openFd`、
  失败就走流式解压」的方式把词典拷到 `files/dict/dict.db`，再 `OPEN_READONLY` 打开。
  这样不必给 gradle 加 `noCompress`，APK 下载体积也更小。
- 版本标记用文件长度（`files/dict/dict.db` 的长度 vs 记录值），换词典后会自动重新解包。
- 查询由 App 侧小写化后 `WHERE word = ?`；前缀联想用 `WHERE word LIKE 'appl%'`。
