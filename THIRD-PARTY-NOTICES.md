# 第三方数据与许可说明

本项目自身的代码以根目录 [LICENSE](LICENSE)（MIT）授权。
下面这些**不是**本项目原创，各自遵循原始许可。

## 1. 离线词典数据：ECDICT

- 仓库：https://github.com/skywind3000/ECDICT
- 许可：MIT
- 使用方式：取 `ecdict.csv`，做筛选、清洗、去重后重新打包成 `assets/dict.db`
  （脚本见 `data/dict/build_dict.py`），条目内容本身未改动。
- 完整许可见 [data/dict/LICENSE.md](data/dict/LICENSE.md)。

ECDICT 在自己的 README 里说明，它的词条来源包含公开的考试大纲词汇表、
`cdict-1.0-1.rpm`（Linux 下的开源字典数据）、BNC 语料库词频以及社区贡献。
若你要把本项目用于商业发行，建议自行复核上游的最新许可与数据来源说明。

## 2. 在线服务

以下能力由各自服务方提供，使用时需遵守对应服务条款，本项目不附带任何数据：

- **dictionaryapi.dev** —— 英英释义、例句、发音 URL
- **MyMemory** —— 机器翻译
- **有道词典**（`dict.youdao.com/jsonapi`）—— 中文释义与双语例句
- **百度翻译 / 有道智云 / Azure Translator / Google 翻译** —— 需要用户自备密钥

## 3. Android 开源库

ML Kit 文字识别（Google）、CameraX、Jetpack Compose、AndroidX 等，
分别遵循各自的开源许可（多为 Apache-2.0）。详见各库随 AAR 附带的 LICENSE。
