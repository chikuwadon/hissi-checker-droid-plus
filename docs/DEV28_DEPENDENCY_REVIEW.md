# dev.28 依存関係・互換処理の整理

基点: v1.1.0-dev.27 (c52259af)。Android CI、CodeQL、Actions lockfile、監査付きGradle依存関係提出、自動依存関係提出は成功。署名済みQA APK生成済み。

## 直接依存関係の確認
- jsoup: HTML解析
- AndroidX Core: WindowCompat、ViewCompat、WindowInsetsCompat
- AndroidX Activity: OnBackPressedCallback
- AndroidX Fragment: FragmentActivity、Fragment、FragmentContainerView
- AndroidX Lifecycle runtime: Activity/Fragmentライフサイクル基盤（Lifecycle.Stateを利用する構成）
- AndroidX Lifecycle viewmodel: 検索画面と履歴画面のViewModel
- JUnit: JVM単体テスト専用

直接依存は既存の責務を維持する。推移依存に偶然含まれていることだけを理由に削除しない。今回バージョン、AGPセキュリティ固定、minSdk、targetSdk、署名設定は変更しない。

## 整理
全4検索サービスのネイティブ対応後に不要となったブラウザ専用分岐を除去。明示的な外部ブラウザ操作は保持。廃止済みKyodemo全過去ログ向けの無制限板解析入口を除去し、板指定の解析に統一。範囲指定がない場合は取得済みページを再利用。バージョン表示のPackageManager呼び出しを既存互換ヘルパーに統合。

## 残件
未使用依存の全面解析・全ソース監査・端末性能実測・旧OS実機回帰・セキュリティ最終確認は別工程。互換Activityと旧OS対応分岐は削除しない。dev.28のCI・実機確認はコミット後に実施。
