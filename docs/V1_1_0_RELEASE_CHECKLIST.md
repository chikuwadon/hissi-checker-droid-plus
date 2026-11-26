# v1.1.0 正式リリース前確認（dev.32）

## 基点 dev.31
- 0cf11308aed175b9d0a55821eb555b82b1cd319b
- Android CI 45859549946、CodeQL 45859550421、監査付き依存関係提出 45859549891、自動依存関係提出 45859539284、Actions Lockfile 45859549926 は全て成功
- dev.30のCodeQL Java/KotlinのHTTP 429による中断は後続CIで解消
- devはmain比39コミット先行、0遅延、PR #37 open（変更前）

## dev.32
「このアプリについて」の機能・過去ログサービス・設定・データ取り扱い・ライセンス情報を更新。実行時バージョン表示は維持。README、CHANGELOG、PRIVACYを開発中の仕様に更新し、公開済みv1.0.5へのリンクは維持。追加のソース契約テスト2件。SDK、ライブラリ、署名、DB形式、検索ロジックは変更しない。

## リリース判定条件（未完了）
- dev.32 CI全項目成功と署名済みAPK
- 実機での総合回帰: docs/DEV30_REGRESSION_REVIEW.md の確認表。特に4過去ログサービス、板検索、IME、履歴保持、ChMate/Haiagaru、エッヂWebViewフォールバック
- GitHub Security画面でDependabot・Code scanning・Secret scanningの未解決アラート確認（接続ツールでは取得不可）
- 必要に応じて旧OS実機・パフォーマンス・全履歴機密情報監査
- ユーザーの正式リリース許可
- 許可後に正式バージョンへ更新、READMEの正式版リンク、CHANGELOGの未リリース表記を更新してmain統合と署名APKの公開を実施

dev.32の実機検証、CI成功、アラート確認は本コミット作成時点では未確認。正式リリースやmainへのマージは行わない。
