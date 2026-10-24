# オフライン板一覧データ

`app/src/main/assets/offline_boards.tsv` は、5chの公開板メニューから独立して生成したデータです。
## 出所とフォーマット

- 取得元: https://menu.5ch.io/bbsmenu.json
- 取得日: 2026-10-23 15:04 UTC
- 公式メニューの `last_modify`: `1792767840`
- 収録: **933板 / 49カテゴリ**
- フォーマット: UTF-8、1行に「カテゴリ名・板ID・板名」をタブ区切りで記録
- URL・識別子の形式を検証し、板ID重複を除外してカテゴリ・板IDでソート
- 出力SHA-256: `c28ceeeb53b73808c20adce61ec1db1d51dc6355b3222a5b2e8f252ef5bdb423`
- 取得JSONのSHA-256: `4121cc00ffabd20b61cb183256771258a32a7ff57a25518bce544ed9b6f2a7ac`

## 再生成

Python 3のみで実行できます。追加のPythonパッケージは必要ありません。

```bash
python3 tools/generate_5ch_snapshot.py \
  --output app/src/main/assets/offline_boards.tsv \
  --metadata /tmp/offline_boards.meta.json
```

再生成する場合は、取得元の更新内容を確認してから、必要な差分だけを反映してください。

## アプリの取得優先順位

1. 端末に保存した5ch公式メニュー（JSON、HTML）
2. 5ch公式メニューからのオンライン再取得
3. 本アプリに同梱するオフライン版TSV

端末内の既存板キャッシュやお気に入りは削除しません。
公式メニューの内容は変化するため、同梱一覧は更新時点のスナップショットです。

## ライセンス

板名・板ID・カテゴリなど、5ch公式メニューを元にしたデータは本プロジェクトのMIT Licenseの対象外です。詳細は[ライセンス適用範囲](LICENSING_SCOPE.md)を参照してください。
