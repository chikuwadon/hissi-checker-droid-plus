# CodeQL Java/Kotlin migration

## Problem confirmed on 2026-11-11

The legacy GitHub CodeQL **default setup** executes Java/Kotlin extraction using `build-mode: none`. Its run on main reported that all **34 Kotlin files were excluded**, while 33 Java unit test files were scanned. The same job attempted to resolve dependencies using Gradle 8.6, but the configured Android Gradle Plugin requires at least Gradle 9.6.

Original run: https://github.com/chikuwadon/hissi-checker-droid-plus/actions/runs/42975771459

## Tested replacement

The repository uses Gradle 9.9, JDK 17, Android Platform 37.0 and Build Tools 36.0.0. For CodeQL, initialize `java-kotlin` in `manual` build mode **before** running the Gradle build, with the build cache disabled and tasks forced to execute. Analyze Python and GitHub Actions separately.

A dry-run confirmed **34/34 Kotlin and 33/33 Java files** were analyzed, and Python and GitHub Actions analyses also succeeded:

https://github.com/chikuwadon/hissi-checker-droid-plus/actions/runs/42976849810

This dry-run does **not** upload results while default setup remains active.

## Required activation procedure

1. In repository **Settings > Advanced Security > CodeQL analysis**, choose **Switch to advanced**, then **Disable CodeQL** for the previous default setup. Do not leave both configurations active.
2. Update `.github/workflows/codeql-advanced.yml` for production: trigger on `push` to main, `pull_request` to main and a weekly schedule; change CodeQL analyze input from `upload: never` to `upload: always` (or remove the input); retain `security-events: write`.
3. Run `gh actions-lock` and `gh actions-lock --verify`, commit the lockfile and workflow together, and merge through a PR.
4. Verify that the first main CodeQL analysis uploads results and includes Kotlin, Java, Python and Actions. Confirm the old warning is no longer reported by the *current* configuration.

Do not disable default setup until the tested replacement is ready to merge. The original default configuration warning may remain visible in historical run details even after current scans succeed.
