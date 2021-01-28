
## POC av hvordan en potensielt kunne gjort ende-til-ende signering mellom utvikler-PC og deployment-agent (feks. NAIS deploy(?))


### Hva?
 - Utvikler gjør endringer i kode på lokal Mac
 - Utvikler signerer endringene med sin private-key (på Yubikey eller på fil eller annet) før commit + push.
 - Signaturen committes og pushes sammen med kode-endringene.
 - Signaturen sendes til deploy-agent via GitHub-actions (eller annen CI-pipeline) og deploy-agent verifiserer signaturen mot docker-imaget som skal deployes.
    
### Hvorfor?
- Deployment-agent kan verifisere at docker-imaget inneholder nøyaktig samme kode, biblioteker og base-image som det utvikler hadde lokalt på sin PC når han/hun pushet endringen.
- Tilgang til utviklers GitHub-konto vil ikke være tilstrekkelig for å kunne deploye kode. Det eneste man kan gjøre fra en kapret GitHub-konto vil være å deploye tidligere versjoner av appen.
- Utvikler kan signere _hele_ docker-imaget ende-til-ende men allikevel slippe unna med å bare pushe en liten kodeendring + en liten signatur.

### Hvordan?

Docker-imaget inneholder en "Id" som skal være en SHA256-hash over config-json-filen i docker-imaget.

Config-JSON-filen igjen inneholder en liste over SHA256-hasher over inneholdet i hver enkelt layer (i form av TAR-filer). 
Altså har nødvendigvis to docker-imager med samme "Id" også akkurat samme innhold.

Tanken er da at dersom utvikler genererer en signatur over "Id" (som er en sha256-hash),
og dersom denne signaturen sendes til deploy-agenten,
og dersom deploy-agenten er i besittelse av utviklers public-key,
så kan deployment-agenten ekstrahere "Id" fra docker-imaget og ved hjelp av public-keyen og signaturen
verifisere at utvikler har gått god for akkurat dette docker-imaget.

(repo-digest som også kan brukes til å f.eks. pinne et spesifikt docker-image fra et repo kan ikke brukes til dette,
for denne er visstnok avhengig av hvilket repo docker-imaget er pushet til. Den kalkuleres vel heller ikke før imaget er pushet,
sånn at et lokalt bygd docker-image _har_ faktisk ikke noe RepoDigest,
mens "Id" er samme lokalt og remote, siden selve innholdet i imaget er det samme)


### Og hvordan få til det?

- Første bud er at docker-imaget som utvikler signerer lokalt er nøyaktig samme docker-image som det som blir komponert i CI-pipelinen.
  - Vanlig ```docker build``` er desverre ikke helt deterministisk. 
Det blir typisk noen litt tilfeldige forskjeller i resultatet når man bygger docker-imaget _en_ plass vs når man bygger en _annen_ plass.
  - Byggsystemet Bazel har laget sin egen docker-build nettopp p.g.a. dette (https://blog.bazel.build/2015/07/28/docker_build.html). 
    Derfor brukes det i denne POCen Bazel-bygging av docker-image i stedet for ```docker build``` + Dockerfile.

- Videre må de tingene som utvikler kopierer inn i docker-imaget også gjøres helt likt lokalt som i CI-pipen.
  For en java/kotlin-app er dette typisk en JAR-fil ("app.jar"), 
  og da er det viktig at tidsstempler + rekkefølge på innholdet i JAR-fila blir det samme.
  I denne POCen fikses det med en liten kode-snutt i ```build.gradle.kts``` som rett og slett løper gjennom JAR-fila som gradle genererte,
  setter filetime til 1.1.1970 på hvert element og sorterer de i en forutsigbar rekkefølge.
  
- Så må selvfølgelig deploy-agent kunne verifisere signaturer, og da må en ha en måte for denne å få tak i verifiserte public-keyer med tilknyttede rettigheter.
Dette kan jo være enkelt eller avansert; per individ, per team etc. 
  En kan jo også alltids dra det langt og tenke seg delegering med "rot-certs" (f.eks. per "team") som igjen utsteder f.eks. tidsbegrensede "sertifikater" til sine medlemmer 
  (som isåfall vil kunne begrense antall public-keys deploy-agent må holde oversikt over).
  Og det hele burde sikkert vært "opt-in"... f.eks. at man kan markere apper og/eller namespaces som "protected" (i.e: krever signatur)

### Demo:

For å bygge dette lokalt må en ha installert Bazel (3.7): ```brew install bazel```

Docker må også være installert for å signere (brukes for å plukke ut "Id"-sha256´en).

Så:

```./gradlew clean build```

For å generere et tilfeldig test-nøkkel-par: 

```./genkeys.sh```

PrivateKey havner da i "key.pem" som ligger i .gitignore-fila. Public-key havner i "key.pub".

For å signere:

```./sign.sh```

Denne bygger docker-imaget med Bazel (viktig at gradle-build er kjørt først), 
plukker ut "Id" og genererer en signatur over denne, som den legger i "imageid.sign".

"key.pub" og "imageid.sign" må sjekkes inn sammen med eventuelle kode-endringer.

```./preverify.sh``` brukes i CI-pipelinen for å sjekke om signaturen stemmer (trenger da key.pub og imageid.sign), og avbryter bygget hvis ikke.

Ved "ordentlig" ende-til-ende-verifisering med verifisering på deploy-agent 
måtte også "imageid.sign" vært med (eventuelt som en hex-string-verdi i en yaml-fil f.eks.),
og en eller annen peker til PublicKey, men det er uansett greit å gjøre public key tilgjengelig i CI-pipelinen
for å kunne preverifisere.. for det er jo ikke noe vits å pushe til registry eller prøve deploy dersom signaturen uansett vil feile.

Så:

Commit en endring uten å oppdatere signatur -> Bygget feiler

Signer og commit signaturen -> Bygget går igjennom igjen.... burde hvertfall...  :-)




