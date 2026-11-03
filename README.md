# 词镜 WordLens · 拍照读英文、点词查释义的安卓 App

拍一页英文（课本、真题、说明书、菜单、路牌），App 在手机本地把文字识别出来，
**点任意一个单词**就出音标、中文释义、英文释义和例句、原句、朗读，一键进生词本，
之后按间隔重复自动排复习。**照片不联网上传**，OCR 用的是打进 APK 的 ML Kit 端侧模型。

- minSdk 26（Android 8.0+）· Kotlin + Jetpack Compose · 单模块 `:app`
- 识别：ML Kit Text Recognition v2（bundled，无需 Google Play 服务下载模型、无需联网）
- 词典：内置离线英汉词典 **114,247 词 / 23.7 MB**（数据源 [ECDICT](https://github.com/skywind3000/ECDICT)，MIT）
- 可选增强：免费英文词典 API、机器翻译兜底、你自己的 OpenAI 兼容接口（AI 详解）

---

## 一、不装任何开发环境，直接拿到 APK（推荐，适合就在手机上操作）

本仓库带了 `.github/workflows/build-apk.yml`，GitHub Actions 会替你编译。

1. 在 GitHub 新建一个仓库（Private 也可以）
2. 把本目录内容传上去，两种方式任选：
   - **网页上传**：仓库页 → `Add file → Upload files`，直接把整个文件夹拖进去
     （`app/src/main/assets/dict.db` 约 24 MB，在网页单文件 25 MB 限制内）
   - **命令行推送**（手机上就能做，需要 [PAT](https://github.com/settings/tokens)，勾 `repo` 权限）：
     ```bash
     cd WordLens
     git init && git add -A && git commit -m "WordLens 1.0.0"
     git branch -M main
     git remote add origin https://<你的用户名>:<PAT>@github.com/<你的用户名>/wordlens.git
     git push -u origin main
     ```
3. 仓库 → **Actions** → 左侧 `Build APK` → **Run workflow** → 等 5~10 分钟
4. 进这次运行记录，最下面 **Artifacts** 里下载 `wordlens-debug-apk`，解压得到 `app-debug.apk`
5. 手机上点开安装（允许「安装未知应用」）。想让别人也能一键下载，就打个 tag：
   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```
   workflow 会自动建 Release 并把 APK 附在 Releases 里。

> Workflow 里会先跑 `:app:testDebugUnitTest`（纯逻辑单测），再 `assembleDebug`。
> 用的是 JDK 17 + Gradle 8.14.6（wrapper 已在仓库里）。

## 二、用 Android Studio 编译

`Open` 本目录即可（AGP 8.13.2 / Gradle 8.14.6 / Kotlin 2.2.21，需要 JDK 17）。

```bash
./gradlew :app:assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # debug key 签名，方便自用；上架请自己配 signingConfig
./gradlew :app:testDebugUnitTest    # 跑 WordNormalizer / OcrCodec / TextFlowBuilder 的单测
```

---

## 三、功能与页面

| 页面 | 能做什么 |
|---|---|
| 📷 拍照 | CameraX 预览、拍照识别、从相册选图（Photo Picker，不要存储权限）、前后摄像头切换；下方「最近识别」可回看 |
| 阅读页 | 三种视图：**单词视图**（逐行可点）· **原图对照**（按 OCR 词框命中，图上画框）· **纯文本**（连续阅读）；复制全文、删除记录 |
| 点词卡 | 音标、中文释义（按词性分行）、英文释义与例句、词形变化（复数/过去式/三单…）、考试标签（四级/六级/考研/雅思…）、原句 + 朗读、加入生词本、AI 详解 |
| 📚 生词本 | 搜索、展开看详情、删除；「复习 N」显示到期数量 |
| 复习 | Leitner 间隔重复：想→翻答案→自评。记得就升级（1/2/4/7/15/30 天），忘了回到第 0 级并 10 分钟后再出现 |
| ⚙️ 设置 | 查词来源开关、AI 接口配置、自动朗读与语速、离线词典状态与词数、清网络缓存 |

### 查词的优先级（离线优先，断网可用）

```
1. 内置离线词典（点词 0ms，词形还原后重查：studying → study）
2. 免费英文词典 dictionaryapi.dev   ← 补英英释义 / 例句 / 发音 URL（结果缓存 30 天）
3. MyMemory 机器翻译 en→zh          ← 离线没收录的词给一句中文
4. 你的 AI 接口（OpenAI 兼容）      ← 只在点「AI 详解」按钮时调用
```
第 2、3 步失败不影响使用，只是少英文例句——UI 会照旧显示离线结果。

---

## 四、目录结构

```
app/src/main/java/com/wordlens/app/
├─ WordLensApp.kt / AppContainer.kt   # Application + 手写依赖容器（不引 Hilt）
├─ MainActivity.kt                    # edge-to-edge + 分享图片进来的 intent
├─ data/
│  ├─ model/      OcrResult / Definition / WordNormalizer（纯 Kotlin，可单测）
│  ├─ ocr/        OcrEngine（ML Kit → 词级坐标）
│  ├─ db/         AppDatabase（业务库：文档/生词/缓存）+ DictStore（离线词典）
│  ├─ dict/       DictionaryRepository + FreeDictionaryApi + TranslateApi + LlmClient
│  ├─ tts/        TtsEngine（系统 TTS）
│  └─ settings/   AppSettings（SharedPreferences + StateFlow）
├─ ui/
│  ├─ RootScreen / MainViewModel      # 5 个目的地，自写路由（不引 navigation）
│  ├─ capture/ reader/ wordbook/ settings/ theme/
└─ util/          OcrCodec（词坐标序列化）、ImageFiles（缩放与落盘）

data/dict/        离线词典构建脚本（见下）
.github/workflows/build-apk.yml       CI
```

**故意不用的依赖**：Room/KSP、Coil、OkHttp、kotlinx-serialization、Navigation、DataStore、Hilt。
原因很实际：这个工程要在没有本地编译验证的情况下交给 CI，注解处理器和第三方坐标是
最容易「版本对不上就整条流水线红掉」的地方，而它们提供的功能这里都用 ~80 行标准库替代掉了
（`SQLiteOpenHelper`、`HttpURLConnection` + `org.json`、自写底部面板、自写路由）。

## 五、离线词典从哪来、怎么重建

```bash
python3 data/dict/build_dict.py            # 下载 ECDICT → 清洗 → 生成 assets/dict.db
python3 data/dict/build_dict.py --csv ecdict.csv   # 用本地已有的 csv
```
脚本从 77 万条里留下「有中文字义且有牛津/Collins/词频/变形/考纲标签」的词，
同词去重保留信息最全的一条，超 40 MB 时按词频名次收紧，最后 `VACUUM` 并自检
（行数、非小写数、空释义数、`integrity_check`、`journal_mode`、样例词）。
当前产物：**114,247 词，23.7 MB**，统计见 `data/dict/build/build_stats.json`。

App 首次启动会把它从 assets 解包到 `files/dict/dict.db` 再只读打开
（asset 被 aapt 压缩也没关系：`DictStore` 会走 `openFd` → 失败则流式解压写出，两条路都测过逻辑）。

许可与署名见 `data/dict/LICENSE.md`。

## 六、常见问题

| 现象 | 处理 |
|---|---|
| CI 报 `Could not find com.google.mlkit:...` | `settings.gradle.kts` 的 `repositories` 必须有 `google()`；ML Kit 不在 Maven Central |
| CI 报 `Dependency ... requires compileSdk 37` | 说明某个库被自动升到了更新的版本。把 `gradle/libs.versions.toml` 里对应库降回本文标注的版本，或把 `compileSdk` 改成 37（需要 AGP ≥ 9.1） |
| `./gradlew` 提示找不到主类 | wrapper jar 没提交上去。用 Android Studio 重新生成，或 `gradle wrapper --gradle-version 8.14.6` |
| 点词只有中文、没有英文例句 | 设置里打开「免费英文词典」；该站点偶尔限流，多点一次或换词重试 |
| 识别不出文字 | 拍正、拍清楚、一行文字尽量占满屏宽；也可以改用「原图对照」视图点词，命中区域按词框算 |
| 朗读没声音 | 系统缺英文 TTS 引擎：设置 → 无障碍 → 文字转语音输入，装一个 Google 文字转语音 |
| 想减小 APK | 打开 release 的 `isMinifyEnabled = true` 并按 `app/proguard-rules.pro` 注释补规则 |

## 七、隐私

- 图片只存在 App 私有目录（`files/documents/`），OCR 全程在手机本地完成，不上传。
- 只有你主动打开的在线查词/翻译/AI 功能会发网络请求，请求内容仅为你点的那个单词（AI 详解会带上那一句上下文）。
- AI Key 存在本机私有偏好文件，不写日志、不备份（`allowBackup=false`）。
