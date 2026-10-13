# Releasing Wishday

Releases are built, signed and published by GitHub Actions. You tag a version and the pipeline does the rest.

## Pipeline

| Workflow | Runs on | What it does |
|---|---|---|
| `.github/workflows/ci.yml` | Every push to `main` and every PR | Debug build, unit tests, lint |
| `.github/workflows/release.yml` | Pushing a `v*` tag, or a manual run | Signed APK and GitHub release |

The release workflow, in order:

1. Checks that the tag matches `versionName` (tag `v1.4` needs `versionName = "1.4"`).
2. Checks that `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` exists.
3. Runs the unit tests and builds the release APK with the signing key from GitHub secrets.
4. Stops if the APK isn't signed with the release certificate. A different key would stop existing installs from updating.
5. Publishes the release as **Wishday X.Y** with `wishday-X.Y.apk` attached. The notes are the English changelog plus the certificate fingerprint.

If any step fails, nothing is published.

### Lint

Lint findings that predate CI are recorded in `app/lint-baseline.xml`, so CI fails only on new ones. If you fix one of them, regenerate the baseline:

```
./gradlew updateLintBaseline
```

## Making a release

1. In `app/build.gradle.kts`, bump `versionCode` (add 1) and `versionName`.
2. Add the changelog, one `• ` bullet per line:
   - `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (required)
   - `fastlane/metadata/android/es-ES/changelogs/<versionCode>.txt` (optional, for the Spanish store listing)
3. Commit, tag and push:
   ```
   git commit -am "Version X.Y: ..."
   git tag vX.Y
   git push origin main vX.Y
   ```
4. Watch it run: `gh run watch -R cybersecurityhandle/wishday`

To release a tag that's already on GitHub (for example after a failed run):

```
gh workflow run release.yml -R cybersecurityhandle/wishday -f tag=vX.Y
```

## Signing

| | |
|---|---|
| Key | `~/.android-keys/wishday-release.jks` |
| Passwords | `~/.android-keys/wishday-release.properties` |
| Certificate SHA-256 | `ab:34:87:4e:5b:a4:d0:15:68:11:aa:88:6e:ee:4c:81:cf:f4:6f:92:55:62:06:ae:8f:b0:61:18:10:46:9a:40` |

Local release builds (`./gradlew assembleRelease`) sign with these files automatically. Without them, the build produces an unsigned APK.

GitHub Actions uses copies stored as repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. To set or replace them from the local files (nothing secret is printed):

```
tools/set-release-secrets.sh
```

**Back up `~/.android-keys/`** somewhere safe, such as a password manager or an encrypted drive. GitHub secrets can't be downloaded again. If the key is lost, no future version can update anyone's installed app.

## F-Droid

Submitted for inclusion in [fdroiddata !52412](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/52412), from the `net.stuckless.wishday` branch of the fork cloned at `~/git/fdroiddata`. The submitted `metadata/net.stuckless.wishday.yml`:

```yaml
Categories:
  - Calendar & Agenda
  - Phone & SMS
License: GPL-3.0-or-later
AuthorName: cybersecurityhandle
SourceCode: https://github.com/cybersecurityhandle/wishday
IssueTracker: https://github.com/cybersecurityhandle/wishday/issues
Changelog: https://github.com/cybersecurityhandle/wishday/releases

AutoName: Wishday

RepoType: git
Repo: https://github.com/cybersecurityhandle/wishday.git

Builds:
  - versionName: 1.3.1
    versionCode: 5
    commit: eb65885dc6137da9baf3858cc48ffa951a9f63b0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.3.1
CurrentVersionCode: 5
```

To change the file while the MR is in review, edit it in `~/git/fdroiddata`, commit and `git push`. The MR updates by itself. Pipelines on the fork don't run because the GitLab account isn't identity-verified; the MR asks a maintainer to run CI instead.

Once the MR is merged, `~/git/fdroiddata` isn't needed. Later changes to the metadata, such as AuthorName, go in a new MR.

Notes:

- F-Droid builds and signs the app with its own key. Its APKs can't update GitHub installs, and GitHub APKs can't update F-Droid installs.
- Once the app is accepted, `UpdateCheckMode: Tags` picks up each new `vX.Y` tag automatically. F-Droid reads the store text, icon, screenshots and changelogs from `fastlane/`.
- Reviewers may ask for the `NonFreeNet` anti-feature because the app can open WhatsApp.
