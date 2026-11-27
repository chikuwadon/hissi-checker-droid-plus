# dev.36 ぬこ初回取得・元URL起動

2026-11-27の実機ログ（hissi_lpg(1).txt 7378行）: HTTP=200、text/html; charset=Shift_JIS、リダイレクト0、Cookieなし、標準HTTP UA、受信0B。解析以前に応答本文が空。録画9秒付近ではWebView経由で結果表示、12秒付近でぬこをChromeで開く。元URLはdev.35でコピー向けに解決したが、ぬこのタップ先は検索サービスのままだった。

- ぬこ専用のHTTP取得に実端末のWebViewデフォルトUAを渡す。Cookie、Accept、Accept-Encoding等は今回変更せず、UAの効果を切り分ける。UAが原因と確定したわけではない。
- 空本文を明示し、Content-Length/Content-Encodingを追加。ネイティブ成功・WebView成功をログで区別。HTTP 200解析失敗時の既存フォールバックを保持。
- スレッドタップは元URL優先で既存ThreadOpenerを使用。解決不可時は確認ダイアログから検索サービスを開ける。
- dev.35 Android CI、CodeQL、lockfile、automatic dependency submission成功。audited dependency submissionはMaven Centralの429による失敗。
- dev.36実機・CI結果は未確認。正式リリースなし。
