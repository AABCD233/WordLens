package com.wordlens.app.data

/**
 * GitHub 加速站点。国内直连 github.com / api.github.com 经常不通，
 * 下载词典包、检查更新时按顺序尝试；用户可在设置里一键关闭。
 */
object AccelMirrors {

    /** 全部可用前缀（顺序即优先级，实测 gh-proxy.org 最快） */
    val PREFIXES = listOf(
        "https://gh-proxy.org/",
        "https://gh.llkk.cc/",
        "https://github.moeyy.xyz/",
        "https://ghproxy.cn/",
        "https://gh.api.99988866.xyz/",
        "https://ghp.ci/",
        "https://ghproxy.net/",
        "https://ghfast.top/",
        "https://gh-proxy.com/"
    )

    /** 给一个原始 GitHub URL 生成候选列表；关闭加速时只留直连 */
    fun candidates(url: String, enabled: Boolean): List<String> =
        if (enabled) PREFIXES.map { it + url } + url else listOf(url)
}
