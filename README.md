# AMOLED Stopwatch

A minimal fullscreen stopwatch for Android. Just time, on a true black screen.

[Русский](README.ru.md) · [Build](docs/BUILDING.md) · [MIT](LICENSE)

![AMOLED Stopwatch on a Samsung Galaxy S25 Ultra](docs/screenshots/stopwatch.jpg)

- True black AMOLED theme. Large Montserrat Bold digits.
- Fullscreen landscape, in either direction.
- Hours, minutes, seconds. No menus or extra buttons.
- Offline. No ads, analytics, accounts, or permissions.

| Tap the digits | Action |
| --- | --- |
| Tap | Start, pause, or resume |
| Double-tap while paused | Reset |

The screen stays awake while the app is open. Timing continues when the app is
in the background or the phone is locked. After a reboot, the last saved time
returns on pause.

The digits update once per second and shift slightly once per minute, including
while paused. Pixel shifting is a precaution, not a guarantee against OLED burn-in.
Brightness and the physical display refresh rate follow your system settings.

<details>
<summary>Another screenshot</summary>

![AMOLED Stopwatch showing hours, minutes, and seconds](docs/screenshots/stopwatch-hour.jpg)

</details>

## Build

Android 8.0+. Native Java, no runtime dependencies or Gradle.
See [build instructions](docs/BUILDING.md) to create an installable APK.

## License

Code: [MIT](LICENSE). Montserrat digits subset: [SIL OFL 1.1](app/src/main/assets/licenses/Montserrat-OFL.txt).
