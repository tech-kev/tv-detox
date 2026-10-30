# tv-detox – Chromecast-Fork

Eigene Version von [Ansub/tv-detox](https://github.com/Ansub/tv-detox) (Basis: v1.5.7, `b22a1e3`) für einen
Chromecast mit Google TV (Android 14). Änderungen liegen auf dem Branch `chromecast-home`, `main` bleibt
gleich mit dem Original.

## Was anders ist

**Home-Taste (`HomeButton.java`)**
- Der Chromecast gibt die Home-Taste nie an Bedienungshilfen weiter: Google TV startet immer zuerst
  (per logcat belegt, `PhoneWindowManager$DisplayHomeButtonHandler`). Abfangen wie bei Plume geht hier nicht.
- Die Fernbedienung schickt pro Druck zwei bis drei Home-Befehle. Das Original legte sich nach dem ersten
  über Google TV und ignorierte die nächsten, sodass Google TV stehen blieb. Jetzt legt sich tv-detox sofort
  drüber und noch einmal, wenn weitere Home-Befehle nachkommen. Google TV ist ca. 0,3 s zu sehen.
- Gilt auch für langes Drücken (Google-TV-Dashboard).
- Interne Fenster von Google TV (`android.widget.FrameLayout`) brechen das Drüberlegen nicht mehr ab.
- **Vorhang** (Idee aus [DumbTV](https://github.com/humblemuzzu/dumbtv), siehe [Plume #153](https://github.com/aradhyas8/Plume_Launcher/issues/153)):
  sobald Google TV gemeldet wird, legt der Dienst ein Overlay in den Hintergrundfarben darüber, bis tv-detox
  gezeichnet ist (max. 2 s). Gemessen: Google TV noch ~0,25 s sichtbar, dann ~0,35 s Vorhang, dann tv-detox.
  Früher geht nicht ohne `canRetrieveWindowContent`, das die App bewusst nicht hat.
- Home springt immer nach oben zur ersten App.

**Einstellungen-Kachel (`MainActivity.java`, `Strings.java`)**
- **Layout**: Klassisch, Kompakt, Kino, Minimal (siehe unten). Auch per `--es layout <Name>` beim Start.
- **Google TV öffnen**: bleibt bis zum nächsten Home bei Google TV.
- **Home-Taste abfangen: an/aus**: aus = Home verhält sich wieder wie bei Google TV.
- **Neu starten…**: öffnet über den Home-Dienst das Android-Ausschaltmenü (eine App darf nicht selbst neu starten).

**Wetter unter der Uhr** (alle Layouts), von [Open-Meteo](https://open-meteo.com), alle 30 min. Der Ort wird nur
auf dem TV gespeichert, nicht im Code:

```sh
adb shell am start -n com.ansub.tvhome/.MainActivity --es weather "<breite>,<länge>" --es stamp $(date +%s)
# entfernen: --es weather none
```

**Kino-Layout**
- Hintergrund leuchtet in der Farbe der markierten App (aus Banner/Icon berechnet, einmal pro App), weich überblendet.
- Markierte App größer, mit Schatten in ihrer Farbe; die anderen gedimmt. Keine Namen unter den Kacheln.
- Kacheln leicht durchscheinend auf einem Glas in ihrer eigenen Farbe.
- Unterer Rand läuft wie eine Leinwand ins Dunkle aus; markierte untere Reihen scrollen darüber hinaus.
- „Licht aus“: nach 60 s ohne Taste bleiben Begrüßung, Uhr und Witz; die Apps nur noch schwach sichtbar.
  Der erste Tastendruck weckt nur.
- Begrüßung je nach Tageszeit vor dem Datum.
- „Witz des Tages“ von [JokeAPI](https://v2.jokeapi.dev) (Kategorien Miscellaneous, Dark, Pun, Spooky, Christmas;
  ohne safe mode; Sprache des TVs), zweiteilige Witze mit Pointe in der nächsten Zeile. Einmal pro Tag geladen
  und gespeichert. Dafür hat die App jetzt die Berechtigung `INTERNET`; andere Layouts gehen nicht ins Netz.

| Prime Video markiert | Dritte Reihe markiert | Licht aus |
|---|---|---|
| ![Kino, Prime Video](docs/screenshots/cinema-prime.png) | ![Kino, dritte Reihe](docs/screenshots/cinema-row3.png) | ![Kino, Licht aus](docs/screenshots/cinema-lights-off.png) |

**Speicherhinweis**: oben rechts neben dem Netzwerk-Symbol, wenn weniger als 15 % frei sind (orange unter
10 %). OK öffnet die Speicher-Einstellungen.

**`build.sh`**: `BUILD_TOOLS=<pfad>` wählt andere build-tools (34.0.0 stürzt mit dem Java von Android Studio ab).

## Layouts

| Klassisch | Kompakt |
|---|---|
| ![Klassisch](docs/screenshots/layout-classic.png) | ![Kompakt](docs/screenshots/layout-compact.png) |
| **Kino** | **Minimal** |
| ![Kino](docs/screenshots/layout-cinema.png) | ![Minimal](docs/screenshots/layout-minimal.png) |

## Bauen und installieren

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
BUILD_TOOLS=$HOME/Library/Android/sdk/build-tools/37.0.0 sh build.sh
adb install -r out/tvhome.apk
```

Updates müssen mit demselben `key.jks` signiert sein (nicht im Repo, steht in `.gitignore`). Ohne ihn lässt
sich die installierte Version nur durch Deinstallieren ersetzen; dabei gehen App-Liste und Einstellungen
verloren. Nach einer Neuinstallation den Home-Dienst wieder einschalten:

```sh
adb shell settings put secure enabled_accessibility_services "<andere Dienste>:com.ansub.tvhome/.HomeButton"
```
