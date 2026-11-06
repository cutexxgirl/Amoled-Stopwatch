# Build from source

Windows PowerShell 7, JDK 21 on PATH, Android SDK Platform 36 and Build Tools 36.0.0.
No Gradle or third-party Java libraries are needed.

```powershell
.\build.ps1 -Sdk "$env:LOCALAPPDATA\Android\Sdk"
```

The script runs the JVM tests, compiles resources and Java, builds DEX, aligns
the APK, signs it with a local development key and verifies its signature.
Output: `dist/AmoledStopwatch-dev.apk`. Development keys are local and ignored by Git.

For a release, provide an existing private keystore and supply its passwords
through environment variables `STOPWATCH_STORE_PASSWORD` and
`STOPWATCH_KEY_PASSWORD` (the latter defaults to the store password).

```powershell
.\build.ps1 -Sdk 'C:\Android\Sdk' -Release -KeyStore 'C:\keys\stopwatch.jks' -KeyAlias 'stopwatch'
```

Output: `dist/AmoledStopwatch.apk`. Keep the same release key across updates.
Signing material is never included in the source archive.

The bundled Montserrat subset contains only digits and a colon. The font is
included in the source tree with its OFL license. No font tools are needed to build.

Device checks before a release: start/pause/resume/reset; background and reopen;
lock/unlock; both landscape directions; minute rollover; font clipping; pixel shift
while running and paused. JVM and package checks do not substitute for these tests.
