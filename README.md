
## POC av hvordan en potensielt kunne gjort ende-til-ende signering mellom utvikler-PC og deployment-agent (feks. NAIS deploy(?))


### Hva?
 - Utvikler gjør endringer i kode på lokal Mac
 - Utvikler signerer endringene med sin private-key (på Yubikey eller på fil eller annet) før commit + push.
 - Signaturen committes og pushes sammen med kode-endringene.
 - Signaturen sendes til deploy-agent via GitHub-actions (eller annen CI-pipeline) og deploy-agent verifiserer signaturen mot docker-imaget som skal deployes.
    
### Hvorfor?
- Deployment-agent kan verifisere at docker-imaget inneholder nøyaktig samme kode (kompilert med samme kompilator), biblioteker og base-image som det utvikler hadde lokalt på sin PC når han/hun pushet endringen.
- Tilgang til utviklers GitHub-konto vil ikke være tilstrekkelig for å kunne deploye ny kode. Det eneste man kan gjøre fra en kapret GitHub-konto vil være å deploye tidligere versjoner av appen.
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
  Det er også viktig at koden er kompilert med samme kompilator-versjon, og at det ikke blir med andre byggmiljø-spesifikke ting/metadata i utputten (som f.eks. hardkodede filbaner til WSDL-filer brukt til kodegenerering)
  
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

"key.pub" og "imageid.sign" må sjekkes inn sammen med eventuelle kode-endringer 
("key.pub" bare første gang - eller hvis man bytter nøkkel - og "imageid.sign" hver gang man har gjort en endring som _skal_ deployes 
(trengs ikke hvis ikke endringen skal deployes - så sånn sett fungerer signeringen også som en "Deploy this!"-kommando.))

```./preverify.sh``` brukes i CI-pipelinen for å sjekke om signaturen stemmer (bruker da key.pub og imageid.sign), og avbryter bygget hvis ikke.

Så:

- Commit en endring uten å oppdatere signatur -> Bygget feiler før deploy

- Signer og commit signaturen -> Bygget går igjennom til deploy igjen.... burde hvertfall...  :-)


Tanken er at hvis signatur-verifiseringen feiler i pipelinen, så vil signaturverifiseringen garantert også feile ved deploy,
og det er jo ikke noe vits å pushe til registry eller prøve deploy dersom signaturverifiseringen uansett vil feile.

Men det [preverify.sh](preverify.sh) gjør er da i prinsippet akkurat det samme som det som må gjøres på Deployment-Agent´en for å verifisere docker-imaget.
Innholdet i "imageid.sign" må altså formidles til deployment-agenten, f.eks. som en hex-string-verdi i deployment-yaml.

Hovedforskjellen er at her i "preverify" verifiseres signaturen opp mot "pub.key" som ligger i repoet (hvor det tas for gitt at denne er gyldig/riktig).
Denne public-Key´en kunne også godt vært sendt med (i yaml feks) til deployment-agent, men deployment-agenten er nødt til å
verifisere at denne public-key faktisk representerer noe(n) som har rettigheter til å deploye denne appen (eller til dette namespacet).
Da må nødvendigvis deployment-agent ha tilgang til en liste over hvilke public-keys som er knyttet til hvilke rettigheter.

Dette _kunne_ f.eks. vært en-til-en mellom nøkkel og utvikler; veldig enkelt og greit, og det funker jo, men det _kan_ jo da bli en god del nøkler å holde styr på 
(nye nøkler/utviklere kommer til, nøkler må rulleres eller kommer på avveie og må revokeres etc.)

Alternativt - hvis man ønsker mindre vedlikehold/oppdateringer på server - kan man flytte litt av ansvaret ut til teamene selv,,,
ved å tenke litt PKI(PublicKeyInfrastructure)-ish, hvor deployment-agent kun sitter med en liste over "rot-nøkler", f.eks. én per Team/namespace,
og hvor teamet kun bruker rot-nøkkelen til å "utstede" individuelle nøkler med begrenset varighet (og ev. med begrenset rettighet/"usage") - altså rett og slett "sertifikater".

På deploy-agent-siden vil det da eventuelt ikke være stort _mer_ komplisert enn at man må sjekke to signaturer i stedet for én, 
men hvor man til gjengjeld da kan slippe unna med litt mindre "gjennomtrekk" i publickey-listene.



### Bazel? Deterministisk docker-bygg? Så mye styr da?

Når man har bazel-oppsettet i orden, og når man har cachet base-image, så vil ikke et ny-bygg + re-signering lokalt ta mer enn 5-10 sekunder,
så det er ikke mye overhead med det - faktisk tar det antakeligvis leeenger tid bare å f.eks. gå til GitHub/CI for å starte en deploy.

Å strebe etter et deterministisk bygg tvinger jo også frem en fin forutsigbarhet + "renslighet" i bygge-mekanikken,
men det _kan_ være litt knølete å få til et 100% deterministisk bygg - avhengig av hvor mye egne "greier" man er avhengig av å kopiere inn i docker-imaget.

Som et "fattigmanns-alternativ", i f.eks. en overgangsfase, kan man alternativt tenke seg at hvis man på noe vis kan pause CI-pipelinen som da viser docker-image-Id,
så kan utvikler kan klippe ut den, generere signaur over den lokalt, og så legge signaturen inn i pipelinen for at den kan fortsette.

Da er man ikke avhengig av deterministisk docker-bygg. 
I.e: Man trenger ikke å kunne reprodusere nøyaktig samme image i pipelinen som lokalt.
Det er jo dog hakket mindre elegant, og utvikler må inn på GitHub for å lose deployen igjennom,
og i teorien er man jo da også sårbar for phishing og/eller endringer i pipeline-bygget som ikke utvikler opplevde lokalt - 
i.e: man kan ikke garantere at resultatet er nøyaktig samme som utvikler så for seg.

Enda enklere kan man også la privat-nøkkelen ligge som en "secret" i GitHub-repoet,
men da er man jo tilbake dit at den eneste barrieren for å deploye er tilgang til GitHub-kontoen,
så det vil vel kanksje ikke gi så veeeldig mye mer enn det å bare basere seg på "DEPLOY_API_KEY" i repo-secret.

Men uansett hvordan utviklerne løser signeringen, så vil behovet på deployment-agenten - og det den skal verifisere - være akkurat det samme;
så vil det være opp til team/utvikler hvor sikkert og/eller elegant man ønsker løse det 
(i.e: "offline" signering på utvikler-PC (sikrest + mest elegant) vs. signering i pipelinen (minst sikkert + minst elegant) 
eller en løsning på halvveien.)


### Annet:

- Man vil jo _anta_ at integriteten i et docker-image verifiseres før det får starte, og i hvertfall før et docker-repo godtar å lagre det, 
  men om det er mulig å "cracke" et docker-image ved å bytte ut "Id" (altså sånn at "Id" faktisk _ikke_ er riktig sha256-hash over docker-config-json´en),
  og om det er mulig å allikevel deploye det p.g.a. ev. manglende integritetssjekk av performance-hensyn(?) bør nesten dobbelsjekkes.
  Om så - så går det ev. an å integritetssjekke innholdet på egenhånd.
- Burde selve deploy-parameterne i yaml _også_ signeres? (Hvis ikke den signeres _kan_ jo "angriper" f.eks. provisjonere baser/rettigheter... men får jo riktignok ikke deployet noe kode for å utnytte det)





