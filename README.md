# Meme GPT (Meme Tail Lab Beta 3)

Android source project rebuilt from `Meme-GPT-v1.2-recovery.apk`
(package `com.memegpt.app.beta1`, version 3.0.0-beta3, versionCode 300).

## Layout

- `app/src/main/java/com/memetaillab/beta1/`: the app that runs (MainActivity,
  TrackerService, paper engine, live trader, bot wallet, networking, database).
- `app/src/main/java/com/benknight/mwsl/`: older "Meme Wallet Shadow Lab" code
  that was bundled in the APK. The manifest doesn't reference it, but it's kept
  so no previous feature is lost.
- `app/src/main/res/values/`: app name and theme. The UI is built in code, so
  there are no layout XML files.

## Build

Requires JDK 17+ and the Android SDK (platform 35).

```
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## How it was reconstructed

The source was decompiled from the APK and then checked method by method
against the original bytecode: calls, field accesses, strings, constants,
arithmetic and control flow between calls. Where the decompiler got the logic
wrong, the method was rewritten to match the original bytecode. Behavior is
meant to be identical to the original APK. Examples of what was corrected:

- missing `continue`/`break` statements (paper/live trailing-stop exits,
  signal qualification, retry loops)
- dropped `(double)`/`long` casts (buy/sell ratio, delay averages, time math)
- inverted or relocated conditions (LIVE arming guard, profit factor,
  duplicate-signature checks, fee floors, sync scheduling)
- try/catch scopes (cursor closing, error handling in the tracker loops)
- `AuditScanner.scan`, which no decompiler could recover, rebuilt by hand
  from the bytecode

## Signing

The original APK was signed with a custom key (`CN=Meme Tail Lab Rebuild`),
which is not in this repository. A build signed with a different key cannot
install over the existing app.
