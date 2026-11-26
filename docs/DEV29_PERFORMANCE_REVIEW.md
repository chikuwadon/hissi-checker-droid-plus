# v1.1.0-dev.29 パフォーマンス監査（静的レビュー）

## 基点
dev.28 a3db730。CodeQL、監査付き依存関係提出、自動依存関係提出、Actions Lockfile は成功。Android CI 45850585706 は Maven Central HTTP 429 により :app:processDebugNavigationResources で中断。jspecify 1.0.0 / listenablefuture 1.0 の取得失敗であり、単体テスト・Lint・APK生成には到達していない。

## 今回の対応
- EdgeAdapter: 解析済みDocumentを診断にも使用。診断済み結果の再解析を回避し、リンク集合を再利用。相対URLの診断件数は解析時のbase URLに基づく。
- ResultActivity: 結果の再集計時だけ時間帯集計キャッシュを無効化。サイト集計値を優先し、その場合はローカル走査を省略。グラフ開閉による再集計を回避。
- HourlyChartView: 固定目盛り配列を再利用。
- ArchiveProviderParser: ぬこの件数・ページ範囲解析で同じ文書テキストを再利用。
- HttpTransport: Content-Type のcharset判定を一度にまとめる。明示された不正charsetの場合の既存フォールバック規則、通信先制限、4MiB上限、タイムアウトは変更しない。

## 未確認
実機での処理時間・メモリ・フレーム時間、長時間利用時の挙動、全サービスの実サイト回帰、全依存のセキュリティ最終監査。今回の改善は静的レビューによるもので、速度改善率は実測していない。互換性維持を優先しHTTP圧縮、並列通信、新規依存、SDK変更は行わない。
