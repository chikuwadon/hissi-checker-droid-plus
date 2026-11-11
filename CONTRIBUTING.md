# Contributing

Pull RequestsやIssueでの改善提案を歓迎します。

## 開発環境

- Android SDK Platform 37.0 / Build Tools 36.0.0（compileSdk 37、targetSdk 36）
- JDK 17
- Gradle 9.9.0
- Android Gradle Plugin 9.5.1 (組み込みKotlin対応)
- アプリ本体のソースコードはKotlinへ移行済みです（単体テストの一部はJava）。

`gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

Android 8.0以降をサポートしています。新しい機能で既存の5ch/エッヂの検索やChMate/Haiagaru連携を壊さないようにしてください。

## Pull Request

1. 作業内容をIssueやPRの説明に記載してください。
2. 変更箇所に対応する単体テストや回帰テストを追加してください。
3. ビルド・Lint・単体テストの成功を確認してください。
4. UI・インテント連携を変更した場合はAndroid実機での検証結果を記載してください。未確認ならその旨を記載してください。
5. APIキー・署名鍵・Cookie・個人情報・非公開ログをコミットしないでください。

PRのCIはforkからの投稿でも署名鍵なしで実行します。署名済みQA APKの生成は管理者自身のPRに限定しています。外部PRへの秘密情報提供は行いません。

既存のリリースタグや署名済みAPKの変更、正式Releaseの公開はメンテナーが別途行います。
