# Public用ドキュメント監査記録（Private管理）

対象：v1.0.0検証ブランチの公開候補。ここに記載した監査資料自体は公開ソースに含めない。

## 公開対象

| ファイル | 方針 |
| --- | --- |
| README.md | 一般向けの概要、機能、使い方、開発、ライセンス、変更履歴、謝辞。原作との関係は簡潔に記載 |
| CHANGELOG.md | v1.0.0の公開情報とv0.1.0～v0.1.21の既存変更履歴を維持 |
| LICENSE | MIT Licenseの原文を維持 |
| PRIVACY.md | データ保存、通信、HTTP、Cookie、広告・解析SDK、権限について利用者向けに整理 |
| SECURITY.md | 脆弱性の非公開報告方法を記載。Private運用の鍵管理説明を除去 |
| THIRD_PARTY_NOTICES.md | jsoupの著作権・ライセンス本文を変更せず維持 |
| docs/BOARD_MENU_SNAPSHOT.md | 5ch公式BBSMENU由来の板一覧の出所、形式、生成方法、ライセンス範囲を記載 |
| docs/LICENSING_SCOPE.md | MIT License対象と第三者データの対象外範囲。Private公開準備の経緯を除去 |
| docs/RELEASE_NOTES_v1.0.0.md | ユーザー向けのv1.0.0機能・改善点。内部監査や公開準備の話は掲載しない |

## Private側に残し、公開用ZIPには入れない文書

- docs/PUBLIC_RELEASE_CHECKLIST.md：内部監査チェックリスト
- docs/PUBLIC_EXPORT_PLAN.md：旧Git履歴を公開せず新規リポジトリに移す手順
- docs/REAL_DEVICE_ACCEPTANCE.md：開発者向け端末検証手順
- docs/RELEASE_CANDIDATE_ACCEPTANCE.md：署名付きQAとCIの検証条件
- docs/GITHUB_PUBLIC_SECURITY.md：公開前後の管理者向け設定手順
- docs/SIGNING.md：署名鍵・GitHub Secretsの構成手順
- docs/UI_POLICY.md：開発中のUI指示・検証上の備考
- docs/DOCUMENT_PUBLICATION_AUDIT.md：本監査記録

## GitHub Actions等の取り扱い

- 公開ソースに含める設定：.github/dependabot.yml、.github/workflows/public-ci.yml
- Private専用として除外：build-debug-apk.yml、release-candidate.yml、generate-5ch-snapshot.yml、public-security-audit.yml
- Public用CIは固定署名鍵を要求しない単体テスト、Lint、Debugビルドのみ。
- Public用ZIPには、ビルドに必要なappソース、テスト、Gradle設定、板一覧生成スクリプトを含める。プライベート用の実機QA、履歴監査、署名管理スクリプトは含めない。
- 公開用ZIPは `python3 tools/create_public_source.py` で明示許可されたファイルだけを取得する。履歴なしのアーカイブとマニフェストを生成するのみで、公開操作はしない。

## 最終的に必要な確認

1. 最新コミットからZIPを生成し、全ファイルと相対リンクの一致、許可外ファイルの除外、ソース内の秘密情報を確認。
2. ZIPの公開用CIと実際のソースで単体テスト、Lint、Debugビルドを実行。
3. GitHub Releasesに載せるAPKの出所・署名・versionCodeと新規公開ソースの一致を確認。
4. 新規Publicリポジトリの作成と正式Releaseは、開発者の個別承認を得てから実施する。
