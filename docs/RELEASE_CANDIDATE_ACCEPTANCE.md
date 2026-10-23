# Signed release candidate acceptance
The candidate workflow is limited to the private release-candidate branch. It does not create a GitHub Release, modify main, or change repository visibility.

## Checked automatically
- Java unit tests, Lint for the release build, assembled signed release APK
- Existing persistent signing certificate SHA-256 C9:08:66:1A:C6:52:2B:03:1D:8E:1F:BC:6E:04:19:4B:77:47:88:F3:71:61:E4:B9:79:D9:41:C4:A8:0B:7C:2C
- Application ID com.chikuwadon.hissidroidplus and versionName 1.0.0 / versionCode 23
- Release APK not marked debuggable; bundled third-party license assets

## Not checked by CI
- Install/update and persisted local data on real Android 8/14/16 devices
- ChMate/other bulletin board apps (0, 1, multiple), accurate thread/post navigation
- 5ch and Edge availability, HTTP denial/failure conditions and HTML changes
- Startup/scroll/UI/CPU/memory/network benchmark measurements
- Repository LICENSE choice by the developer, and Public visibility of deleted legacy board data in Git/Actions history
- Authorship of Java code and self-created launcher icon as stated by the developer; removed board asset is not included in the current build
- Owner's GitHub Security settings and full legal approval

Treat this APK only as a private **release candidate**. Public visibility and official release still require explicit separate approval.

## Emulator smoke checks
- API 26, 34, 36 emulator smoke tests pass signed upgrade and board favorite persistence; physical-device tests remain required
- They cannot validate ChMate interop or live board server availability on user phones
