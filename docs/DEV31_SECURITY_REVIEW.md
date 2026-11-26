# v1.1.0-dev.31 セキュリティレビュー

## 基点
dev.30 (3354c60)。Android CI、監査付き依存関係提出、自動依存関係提出、Actions Lockfile は成功。CodeQL Python/Actions は成功。Java/Kotlin は Maven Central の HTTP 429（jspecify/listenablefuture）でコンパイル段階中断。静的解析完了を意味しない。

## 今回の強化
- 外部URLの共通検証: HTTP/HTTPS、実ホスト、ユーザー情報なし、制御文字なし、妥当なポート。未検証スレッドURLは専ブラへの汎用Intent起動ではなくブラウザ経由とする。
- エッヂのWebView: 期待するHTTPSオリジンをロード前、遷移時、DOM取得時、解析結果反映時に確認。混在コンテンツ、第三者Cookie、ポップアップを抑止しSafe Browsingを明示有効化。既存のファイル・コンテンツアクセス無効を維持。破棄済みWebViewの遅延処理を抑止。
- JVM回帰テスト4件追加。

## 確認した既存の対策
Manifestの宣言権限はINTERNETのみ。結果・ホームと連携用aliasの公開は機能上必要、その他Activityは非公開。network_security_configはhissi.org/www.hissi.orgのみ平文通信例外。HTTP通信先制限と応答上限、署名設定、依存監査ワークフローは維持する。現行devのファイルパス一覧には鍵ファイル形式（jks/p12/pem/key）の該当なし。これは内容・全履歴の秘密情報スキャンの代替ではない。

## 残る確認事項
GitHubのDependabot、Code scanning、Secret scanningのアラートAPIは接続側の制限で取得不可。最新アラート件数・全履歴のシークレット監査は未確認。HTTP例外は既存サービス互換性のため維持しており盗聴・改変リスクが残る。WebViewは必要なJavaScriptとDOM storageを維持、HTTPSサブリソースは一律遮断しない。実サイトの認証・フォールバック、専ブラ連携、旧OS、端末バックアップポリシーは別途検証を要する。dev.31 CI成功・実機検証は未確認。正式リリースは別途許可後。
