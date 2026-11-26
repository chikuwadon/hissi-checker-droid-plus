package com.chikuwadon.hissidroidplus

import android.os.Bundle

/** First-stage archive entry points. Native search adapters are not enabled yet. */
class ArchiveActivity : MainSectionActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val body = screen("過去ログ検索", MainNavigation.Destination.ARCHIVE)
        caption(body, "この開発版では検索サイトをブラウザで開きます。アプリ内検索は今後対応予定です。")
        caption(body, "5ch")
        row(body, "ぬこ（外部ブラウザ）") { BrowserLauncher.open(this, "https://n2ch.net/?guid=ON") }
        row(body, "Kyodemo（外部ブラウザ）") { BrowserLauncher.open(this, "https://www.kyodemo.net/sdemo/") }
        caption(body, "エッヂ")
        row(body, "エッヂの過去ログ（3代目）") { BrowserLauncher.open(this, "https://eddiarchive3rd.boy.jp/") }
        row(body, "Kyodemo（外部ブラウザ）") { BrowserLauncher.open(this, "https://www.kyodemo.net/sdemo/") }
    }
}
