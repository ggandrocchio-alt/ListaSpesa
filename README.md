# ListaSpesa

App Android in Kotlin con accesso Google e liste della spesa condivise tramite Firebase Authentication e Cloud Firestore.

## Uso

1. Accedi con un account Google.
2. Crea una lista o inserisci il codice d'invito ricevuto da un membro.
3. Seleziona la lista e aggiungi, spunta o elimina gli articoli. Le modifiche appaiono agli altri membri tramite Firestore. La cache Firestore consente di modificare gli articoli anche senza rete e invia le modifiche al ritorno della connessione.
4. Per invitare un altro account, seleziona la lista e usa **Condividi**. Il codice d'invito dà accesso alla lista: invialo solo a persone con cui vuoi condividerla.

Gli articoli salvati nelle versioni precedenti restano in `SharedPreferences` sul dispositivo. La loro migrazione a Firestore è ancora da implementare.

## Configurazione Firebase

- Progetto: `listaspesaandroid`; package Android: `com.example.listaspesa`.
- Il file `app/google-services.json` contiene gli identificatori del progetto e i client OAuth Android e web necessari all'accesso Google.
- Le regole di accesso sono versionate in `firestore.rules`: i membri possono leggere la lista e gli articoli; un nuovo account può unirsi solo con il codice d'invito.
- La firma Android stabile viene ripristinata dai GitHub Actions Secrets; il keystore non è nel repository.

## Build e verifica

Tutte le build Android e i test delle regole Firestore avvengono su GitHub Actions. Non è richiesto né usato un JDK locale. La pipeline pubblica l'APK `app-debug.apk` come artifact e, su push a `main`, come asset di una GitHub Release.
