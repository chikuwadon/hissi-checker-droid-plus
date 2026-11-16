# AGPのビルド用間接依存：脆弱性対応（2026-11-15）

## 対象と背景

Dependabotで報告された9件は、Androidアプリの`implementation`依存ではなく、Android Gradle Plugin（AGP）9.5.1のビルド用間接依存として検出された。

- JDOM2 2.0.6 → 2.0.6.1：前回のPR #21で対策
- jose4j 0.9.5 → 0.9.6：前回のPR #21で対策
- Bouncy Castle bcprov/bcpkix 1.80.2 → 1.86（3件）
- Guava 33.4.0-jre → 33.7.2-jre
- Commons Lang 3.16.0 → 3.18.0
- Apache HttpClient 4.5.6 → 4.5.14
- Kotlin Gradle Plugin 2.2.10 → 2.4.21

## 最新安定版への追随（2026-11-16、PR #32）

上記の2026-11-15時点の変更履歴は保持し、現在のビルド用クラスパスでは以下の安定版を使用する。

- Bouncy Castle bcprov / bcpkix / bcutil：1.86 → **1.87**
- Guava：33.7.2-jre → **33.8.0-jre**
- Apache Commons Lang：3.18.0 → **3.22.0**
- jose4j：0.9.6 → **0.9.7**

これらの期待値は `tools/verify_gradle_buildscript_dependencies.py`、`tools/validate_resolved_dependency_snapshot.py`、`tools/audit_gradle_dependency_snapshot.py` にも反映し、実際の選択版で検証する。HttpClient 4.5.14は4系の最新、JDOM2 2.0.6.1も現行最新のため据え置く。

## 対策

AGPは最新安定版9.5.1を維持したまま、root `buildscript` classpathにて旧モジュールの推移的取り込みを除外し、修正版のモジュールを直接指定する。

これは**ビルドツールのクラスパスのみ**に適用する。アプリ本体の依存`jsoup`、ソースコード、署名鍵は変更しない。

Kotlin Gradle Plugin 2.4.21は脆弱性の修正済み安定版であるが、JetBrainsの公開する「完全サポート済みAGP」の上限は9.3.1であり、9.5.1との組み合わせは公式には完全保証されない。CIのテスト、Lint、Debugビルド、署名付きQAビルドおよびCodeQLで動作を確認する。Gradle/Kotlinビルドキャッシュは引き続き無効とする。

## 検証方法

- `gradle --no-daemon buildEnvironment` の実際の`requested -> selected`を確認する。GitHub Actionsの`verify_gradle_buildscript_dependencies.py`は選択した依存が修正版と異なると失敗する。
- 単体テスト、Lint、Debugビルド、署名済みQAビルドとCodeQLを確認する。
- Dependabotの個別アラートについてはGitHub上でも再評価後の表示件数を確認する。

## GitHub Dependency Graphの注意

GitHubの「Automatic Dependency Submission」から取得したスナップショットには、AGPが宣言する旧版の**要求元メタデータ**が残ることがある。実際のGradle `buildEnvironment`レポートでは、その要求が修正版に解決されていることを確認済み。

したがって、GitHubの警告数だけでは、実行されるビルド用クラスパスが脆弱であるかどうかを判断できない。一方で、**表示上の7件がすべて解消したとも断定しない**。誤検出を疑ってもDependabot・Dependency Graphを無効化したり、警告を根拠なくDismissしたりしない。正確な対応状況を記録し、AGPに修正済みの安定版が提供された際は優先して置き換える。

参考：
- https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html
- https://docs.github.com/en/code-security/concepts/supply-chain-security/dependency-graph-data
- https://kotlinlang.org/docs/gradle-configure-project.html


## 依存グラフの監査済み提出

GitHubのAutomatic Dependency Submissionや公式Gradle Dependency Submissionは、AGPの公開POM内の要求版（例: Guava 33.4.0-jre）を、実際に選択された版（初回対策時は33.7.2-jre、現在は33.8.0-jre）と別ノードとして提出することがある。ユーザー報告の残件はこの差に起因する。

`tools/resolved-dependency-snapshot.gradle` はGradleの`ResolutionResult`を利用し、通常のビルドとテスト・Lintで実際に解決された依存を収集する。`tools/validate_resolved_dependency_snapshot.py` は修正版の選択状況と参照整合性を検証する。

ただし、実解決グラフだけではAGPのLintなどのツール系モジュールが全て含まれるとは限らないため、公式Gradle Dependency Submissionが出力する**全依存メタデータも保持**する。`tools/audit_gradle_dependency_snapshot.py` は、全メタデータのうち選択版を検証した9モジュールだけを実解決結果へ合わせ、依存の参照も更新する。差分を自動検証し、その他の依存モジュールは削除しない。

本番ワークフローでは、この監査済みスナップショットをGitHub Dependency Submission APIで**手動提出**する。GitHub公式仕様上、手動提出は自動提出より優先される。ただし、自動提出・Dependabot・CodeQLの機能は無効化しない。CIでは古い版の実選択を引き続き検知する。

GitHubの表示でアラートが閉じたかどうかは、**mainのDependency GraphとDependabot Alertsを再評価した結果**で確認する。根拠なくDismissして件数を減らす対応はしない。

- [GitHubの提出形式と優先順位](https://docs.github.com/en/code-security/reference/supply-chain-security/automatic-dependency-submission)
- [Gradle公式のDependency Submission](https://docs.gradle.org/current/userguide/github-actions.html)
