# CodeQL: Kotlin対応のAdvanced setup

## 原因（2026-11-11）

旧CodeQL Default setupの `java-kotlin` 解析が `build-mode: none` で動作していたため、Kotlin 34ファイルが解析されず、Javaのテスト33ファイルだけを解析していた。依存関係の検出ではGradle 8.6が選択されたが、Android Gradle PluginにはGradle 9.6以上が必要だった。

旧設定のログ: https://github.com/chikuwadon/hissi-checker-droid-plus/actions/runs/42975771459

## 対応内容

`.github/workflows/codeql-advanced.yml` を追加して、次の構成で解析する。

- Java/Kotlin: JDK 17、Gradle 9.9、Android SDK Platform 37、CodeQL `build-mode: manual`
- `gradle --no-daemon --no-build-cache --rerun-tasks :app:assembleDebug :app:testDebugUnitTest` の実行によってKotlinの抽出を行う
- Python / GitHub Actions: 別ジョブでCodeQLを実行
- push（main）、pull_request（main）、毎週月曜日03:23 UTC、workflow_dispatchで実行
- 解析結果をCode Scanningへアップロードする（`security-events: write`）
- `.github/workflows/actions.lock` を使いCodeQL Actionの依存を固定し、`gh actions-lock --verify` で整合性を検証

2026-11-11の検証用ワークフローでは、Kotlin **34/34**、Java **33/33**、PythonおよびGitHub Actionsの解析が成功した。

事前のドライラン: https://github.com/chikuwadon/hissi-checker-droid-plus/actions/runs/42976849810

## 運用・確認

従来のDefault setupは新しいAdvanced setupと同時稼働させない。ユーザーがGitHub管理画面で設定を切り替えた後に、本ワークフローの解析結果アップロードを有効化する。

mainにマージした後は、[CodeQL status](https://github.com/chikuwadon/hissi-checker-droid-plus/security/code-scanning/tools/CodeQL/status/) で現在の分析がKotlinを含むこと、各ジョブの終了と結果アップロードが成功したことを確認する。旧設定で生成された歴史的な警告は個々の過去の実行ログに残る場合がある。

CIやAndroidアプリ本体の変更は伴わない。公開済みのリリースタグや署名済みAPKは維持する。
