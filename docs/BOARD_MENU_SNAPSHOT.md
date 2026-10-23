# オフライン板一覧データ

`app/src/main/assets/offline_boards.tsv` は、5chの公開板メニューから独立して生成したデータです。
原作Android APK、逆コンパイル結果、以前の `original_boards.tsv` は生成に使用していません。

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

GitHub Actionsの `Generate independent offline board menu snapshot` ワークフローでも、
同じ生成スクリプトを利用し、確認用のTSVとメタデータを一時成果物として出力します。
**取得内容は自動コミットしません。** 収録数、差分、出所を確認してから更新してください。

## アプリの取得優先順位

1. 端末に保存した5ch公式メニュー（JSON、HTML）
2. 5ch公式メニューからのオンライン再取得
3. 本アプリに同梱するオフライン版TSV

端末内の既存板キャッシュやお気に入りは削除しません。
公式メニューの内容は変化するため、同梱一覧は更新時点のスナップショットです。

**権利と公開範囲:** 公開されているサイトの板名・板IDを基に新規生成したものであり、原作APKの流用とは区別されます。
ただし、公式メニューからの生成という事実だけで、収録データのあらゆる再配布条件が自動的に確認されるわけではありません。
Public化時はサービスの利用条件や更新後の掲載範囲も別途確認してください。
