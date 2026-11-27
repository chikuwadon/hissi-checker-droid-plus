# CI依存関係取得の再試行（v1.1.0-dev.33）

Android CI 45868066883 と Audited Gradle dependency submission 45868067140 は Maven Central の HTTP 429 により :app:processDebugNavigationResources で失敗。対象は jspecify 1.0.0 / listenablefuture 1.0。CodeQL、Automatic Dependency Submission、Verify Actions Lockfile は成功。

明示的なGradleビルド呼び出しに共通のHTTP 429再試行スクリプトを追加。15秒、45秒、90秒の待機後に再実行し、最大4回。429がない失敗は即座に失敗扱い。最終失敗は元の終了コードを返す。依存バージョン、取得先、キャッシュセキュリティ設定、署名条件、テストとLintの必須条件は変更しない。buildEnvironmentの監査出力およびGradle公式の依存提出Actionは既存のまま。

ローカルのモックによるシェル検証: 429が2回後成功するケースは3回で終了0、通常のコンパイル失敗は1回で終了7、連続429は4回で終了9。実際のMaven Central応答およびGitHub Actionsの成功は未確認。アプリのソース変更がないためバージョンはdev.33のまま。正式リリースは行わない。
