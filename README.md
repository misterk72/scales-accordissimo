# Scales Android

Application Android native Kotlin/Jetpack Compose qui reprend le périmètre V1 de la PWA Accordissimo Scales, avec contenu local et fonctionnement hors ligne.

## Contenu

- Navigation Material 3 sombre avec les onglets `Accueil`, `Gammes` et `Metronome`.
- Lecture des accompagnements via Media3/ExoPlayer depuis les assets locaux.
- Generation MIDI hors ligne pour toute selection, avec premiere note, decompte audible, ligne de gamme et accords. Un fallback synthetique PCM reste disponible si le lecteur MIDI du device echoue.
- Manifest local `app/src/main/assets/scales_manifest.json` comme contrat entre contenu musical et application.
- Preferences et favoris stockes avec DataStore.
- Metronome local 1 a 4 temps, tempo libre et presets de la page Gammes.

## Assets

Les accompagnements doivent etre places sous :

```text
app/src/main/assets/scales/{transposition}/{pitch}/{mode}/{key}/{octaves}/{register}/{tempo}/{arrangement}.mp3
```

Le fichier `scales_manifest.json` reference chaque exercice avec ses metadonnees musicales et son chemin audio. L'application signale les assets officiels manquants et garde une lecture d'aperçu synthetique pour permettre le travail hors ligne avant integration des fichiers definitifs.

Un fichier audio de demonstration est inclus pour `ut/440/major/c/1/medium/88/piano`.

Pour importer un pack MP3 autorise, creer un fichier JSON sur le modele de `audio_sources.example.json`, puis lancer :

```bash
python3 scripts/import-audio-pack.py audio_sources.json
```

Chaque entree doit contenir `assetPath` et soit `sourceFile`, soit `sourceUrl`. Le script refuse les chemins hors de `app/src/main/assets/scales/`.

## Build

Le projet utilise le wrapper Gradle 8.13. Le Gradle systeme 9.x peut etre incompatible avec Android Gradle Plugin 8.2.0.

```bash
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon assembleRelease
```

L'APK release de prototype est signe avec la cle debug Android et genere dans :

```text
app/build/outputs/apk/release/app-release.apk
```

## Release

Le workflow GitHub Actions `.github/workflows/android.yml` execute les tests, construit l'APK release et publie un asset telechargeable quand un tag `v*` est pousse, par exemple `v0.1.0`.
