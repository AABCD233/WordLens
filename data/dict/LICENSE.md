# 第三方数据来源与许可

本项目打包的 `app/src/main/assets/dict.db` 由下面的开源词典生成。

## ECDICT （English-Chinese Dictionary）

- 仓库：https://github.com/skywind3000/ECDICT
- 使用文件：`ecdict.csv`
- 许可：MIT
- 上游版权行：`Copyright (c) 2025 Linwei`

ECDICT 在其 README 中说明，它的词条来源包含公开的考试大纲词汇表、`cdict-1.0-1.rpm`
（Linux 下的开源字典数据）、BNC 语料库词频，以及社区贡献。若你要把本 App 用于商业发行，
建议自行复核上游仓库的最新许可与数据来源说明。

使用方式：只取 `word / phonetic / translation / definition / tag / exchange / collins / oxford / frq / bnc`
这些字段，做纯英文拼写过滤、小写归一、同词择优去重后重新打包，没有修改条目内容本身。

---

## MIT License

Copyright (c) 2025 Linwei

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
