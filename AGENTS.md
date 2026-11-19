# WordLens 开发约定（给 AI 会话）

> 本文件给本机 AI 会话看；`/root/workspace/tools/` 里的脚本不在仓库里。

这个仓库的 CI 在 GitHub Actions 上跑，约 1–3 分钟一次；本机没有 JDK/Android SDK，
**不能本地编译**。推送前先用静态预检，能省掉一半 CI 往返。

## 1. 推送前必做

```bash
python3 /root/workspace/tools/precheck.py /root/workspace/WordLens
```

- 退出码 0 才算通过；非 0 会逐条列出 `文件:行号 + 问题`。
- 检查：花括号/圆括号平衡、用了但没 import 的符号、本地 `@Composable` 命名参数对不上。
- 加 `--json` 给脚本读；`--only brace|import|composable` 只跑一类。
- 它不替代编译器，只拦截最常见的低级错误。

## 2. 推送与看 CI

```bash
python3 /root/workspace/tools/wl.py push "提交说明"   # Git Data API，绕开被墙的 git push
python3 /root/workspace/tools/wl.py ci               # 最近一次：短号/耗时/结论/失败步骤/前 5 条 e: file
python3 /root/workspace/tools/wl.py ci --json        # 给面板或脚本读
```

- 日常改动只推 main，CI 产物（4 个 ABI APK）在 Actions artifacts，30 天过期。
- 产物直链：`https://gh-proxy.org/https://github.com/AABCD233/WordLens/actions/runs/<run>/artifacts/<id>`
  （免登录，浏览器直接下）。

## 3. 发版（只在用户明确说“发版”时做）

**不要**日常每次更新都发 Release；旧 Release 永远保留，不删除、不覆盖。

1. 先把 `app/build.gradle.kts` 的 `versionName` / `versionCode` 改好并推送。
2. 写中文说明到临时文件（不用自己写架构提示，脚本会补在开头）。
3. 发布：

```bash
python3 /root/workspace/tools/wl.py publish v2.7.0 /tmp/release-v2.7.0.md --yes
```

- 不带 `--yes` 只预览，不写入。
- 流程：打 tag → 等 CI → CI 建好 **草稿** Release → PATCH `draft=false` + 中文说明。
- 脚本会校验：tag 与 `versionName` 一致、版本号比最新 Release 大、tag 不存在。
- 已发布的 Release 会被拒绝覆盖，没有删除命令。

## 4. 手机面板

装了 `local-wordlens` 插件的主机上：

- 输入框上方状态条：`CI ✅ cec1d040 52s`，点开是 CI 面板。
- 输入框上方「预检」「发版」胶囊；面板也在对话详情页「插件」和「全部功能」。
- CI 面板：失败步骤 + 前 5 条编译错误（每行可点复制，含文件:行:列）+ 打开构建 + 产物直链；
  「重跑构建」只重跑同一个 commit，不推送，触发前二次确认。
- 预检面板：一键跑 precheck、复制问题清单；也可以填提交说明直接「推送 main」，有问题先提醒但不拦。
- 发版面板：填版本号和中文说明 → 预览（检查 tag / versionCode / 是否提到 arm64 或架构 / 是否满 20 字）→ 二次确认 → 打 tag/等构建/发布；README 版本不一致只提示不拦；绝不删除历史 Release。
- 产物面板：Actions 产物 + Release 资产，点一下复制 gh-proxy 直链。

## 5. 安全

- token 只放在 `~/.wl_token`（600）或 `GITHUB_TOKEN` 环境变量，**不要**写进源码、提交或面板文案。
- 不要改 `app/src` 以外的构建脚本时顺手删历史 Release；这是硬约定。
- 本机 `github.com:443` 常被墙；`api.github.com` 可用。下载走 `gh-proxy.org` 前缀。
