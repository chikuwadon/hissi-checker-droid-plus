# GitHubの公開後セキュリティ設定

2026年10月時点のGitHub公式ドキュメントに基づく確認手順です。

## 1. 公開前

- PrivateリポジトリをそのままPublicに変更しないでください。旧Git履歴には過去の板一覧データが残ります。
- 検証済みの作業ツリーから履歴を含まない新しい公開リポジトリを作成する方針です（PUBLIC_EXPORT_PLAN.md）。
- Dependabotの週次更新PR提案は `.github/dependabot.yml` に設定済みです。更新の自動マージは設定していません。

## 2. Publicリポジトリを作成した後

1. GitHubの公開先リポジトリで **Settings → Security and quality → Advanced Security** を開きます。
2. **Dependabot alerts** と **Dependabot security updates** の有効状態を確認します。
3. **Secret scanning** と、提供されていれば **Push protection** の有効状態を確認します。
4. **Code scanning** の設定画面でCodeQL等の解析を検討します。
5. **Private vulnerability reporting** を有効化します。これは一般の報告者が公開せずに問題を報告できる機能で、Publicリポジトリが対象です。
6. リポジトリの **Security** タブから脆弱性報告窓口やDependabotアラートの見え方を確認します。

## プラン・権限の制約

GitHubの公式仕様上、個人所有のPrivateリポジトリではSecret scanning alertsの利用が制約されます。PublicにするとSecret scanningは自動的に動作します。Private vulnerability reportingはPublicリポジトリの管理者が有効化する機能です。現行のGitHub接続から、個別リポジトリのSecurity設定の現在値は取得・変更できませんでした。

公式資料：
- https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-security-and-analysis-settings-for-your-repository
- https://docs.github.com/en/code-security/how-tos/secure-your-secrets/detect-secret-leaks/enable-secret-scanning
- https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/configure-vulnerability-reporting/configure-for-a-repository
