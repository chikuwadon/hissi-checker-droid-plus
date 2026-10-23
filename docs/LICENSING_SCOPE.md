# ライセンスの適用範囲

## MIT License の対象

開発者が独自に実装したソースコード、開発者が制作したランチャーアイコン、および本プロジェクト独自のドキュメントに適用します。著作権表記は `LICENSE` を参照してください。

## MIT License の対象外

- `app/src/main/assets/offline_boards.tsv`：5ch公式BBSMENUの板ID・板名・カテゴリを元に生成した一覧。別途5ch側の利用・再配布条件を確認する必要があります。MITの許諾対象に含めません。
- `app/src/main/assets/third_party_licenses.txt` / jsoupなど：元の第三者ライセンスが優先します（`THIRD_PARTY_NOTICES.md`参照）。
- 5ch、エッヂ、hissi.org、ChMateや他の外部サービスの画面・データ・商標等：本プロジェクトが再利用を許諾するものではありません。

## 公開時の留意点

このLICENSE追加はPrivate検証ブランチの公開準備です。GitHubリポジトリをPublicに変更したりGitHub Releaseを作成したりする操作は行っていません。

過去コミットには以前の板一覧データが残ります。安全な公開履歴で配布する場合は `PUBLIC_EXPORT_PLAN.md` を参照してください。
