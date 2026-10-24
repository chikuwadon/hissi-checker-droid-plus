# クリーンなソース公開手順（Private版は維持）

## 理由

現在のPrivateリポジトリには開発中の差分・Git履歴・旧板データ・過去のActions成果物が残っています。最新ファイルを削除しても過去のリビジョンやActions添付ファイルまでは消去されません。

## 推奨公開方式

1. Privateリポジトリは現状維持。**既存リポジトリのPublic化、mainへのforce push、履歴の破壊はしない。**
2. 検証済みv1.0.0の作業ツリーから `python3 tools/create_public_source.py` で許可したファイルのみを集め、Git履歴なしの公開用ZIPを作成。
3. 公開対象の全ファイル、ライセンス、秘密情報、公開用CIを監査する。特にPrivate専用のQA・監査・署名・公開準備の文書とワークフローをZIPに含めないことを検証する。
4. 安全が確認されたら**別の新規公開リポジトリ**へ初期コミットとしてインポート。元PrivateリポジトリをPublicには切り替えない。
5. 公開初期コミット・タグと署名付きAPKのSHA-256を一致させ、正式Releaseを個別承認後に作成。

## 準備中の事項

- 公開候補ZIPと監査用マニフェストはPrivate GitHub Actions成果物として扱う。生成だけでは公開しない。
- 公開対象のドキュメントはREADME、CHANGELOG、LICENSE、PRIVACY、SECURITY、THIRD_PARTY_NOTICES、BOARD_MENU_SNAPSHOT、LICENSING_SCOPE、RELEASE_NOTESのみとする。
- Private専用資料：PUBLIC_EXPORT_PLAN、PUBLIC_RELEASE_CHECKLIST、REAL_DEVICE_ACCEPTANCE、RELEASE_CANDIDATE_ACCEPTANCE、GITHUB_PUBLIC_SECURITY、SIGNING、UI_POLICY、およびPrivate QA用ワークフロー・監査スクリプトを含めない。
- 公式メニュー由来のオフライン板一覧は開発者の選択に従って同梱する。元のGit履歴は公開しない。
- 新規リポジトリのURL・名称および公開操作は別途承認を得てから決定する。
