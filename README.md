# Lista Spesa

App Android nativa in Kotlin per gestire una lista della spesa. Gli articoli vengono salvati localmente sul dispositivo.

## Sviluppo

Requisiti: JDK 17 e Android SDK con `platforms;android-35` e `build-tools;35.0.0`.

```bash
./gradlew assembleDebug
```

APK generato: `app/build/outputs/apk/debug/app-debug.apk`.

## Funzionalità

- aggiunta di articoli;
- contrassegno degli articoli acquistati;
- eliminazione degli articoli;
- persistenza locale automatica.
