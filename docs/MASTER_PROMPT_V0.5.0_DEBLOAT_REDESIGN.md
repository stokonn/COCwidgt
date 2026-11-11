# COCwidgt v0.5.0 — Master Prompt pro Codex
## Debloat + oprava dat/widgetu + kompletní redesign v jednom updatu

Jsi hlavní Android/Kotlin engineer projektu COCwidgt (dev.stokonn.cocwidgt). Pracuješ v existujícím repozitáři. Cílem je připravit kvalitní, otestovaný update v0.5.0. Neprováděj KMP migraci ani iOS implementaci — ta patří do samostatného pozdějšího updatu.

## 0. Ověř základ

- Pracuj na větvi codex/v0.5.0-debloat-redesign, která vychází z vydaného tagu v0.4.0.
- Před změnami ověř git status, branch, HEAD a tag v0.4.0. Zachovej existující změny.
- DŮLEŽITÉ: výchozí větev main v repozitáři obsahuje starší kód 0.2.0. Nepoužívej ji jako základ. Základem je commit a23b3ad862e9fb788c07c26e1fb71901fc2d6ee7.
- Zmapuj celý projekt, datový tok od importu/API až po widget a všechny obrazovky. Přečti README, manifest, Gradle konfiguraci, workflow, parser, katalog, API klienta, worker, token storage, widget a testy.
- Před implementací zaznamenej baseline: testy, lint, debug/release build a velikost APK.

## 1. Cíl updatu

V jednom uceleném updatu vyřeš:
1. debloat a audit závislostí,
2. opravy dat a jejich mapování,
3. opravy rozložení widgetu,
4. kompletní redesign widgetu,
5. kompletní redesign hlavního rozhraní aplikace,
6. regresní testy, dokumentaci a ověřený build.

Nesmí jít o povrchní kosmetický patch. Zachovej funkční import JSON, API sync, šifrování tokenu, lokální countdowny, artwork, resizability widgetu a kompatibilitu s již vydanou aplikací.

## 2. Datové chyby

### Player identity
- Odstraň duplicitní výskyty Player Tagu v UI i datovém toku. Nejprve zjisti, kde vznikají; nedělej pouze vizuální workaround.
- Player Tag normalizuj (trim, uppercase, jednotná pravidla pro #) a používej konzistentně mezi importem, API profilem, preferencemi a widgetem.
- Import a API profil musí být spojeny podle normalizovaného tagu. Při nesouladu tagů nesmíš tiše zobrazit jméno nebo profil jiného hráče.
- Doplň a správně zobraz jméno hráče. Preferuj validní API jméno, případně jméno z exportu; prázdný string nebo whitespace nesmí přebít validní jméno. Pokud jméno není dostupné, zobraz neutrální fallback, ne Player Tag vydávaný za jméno.
- Zkontroluj synchronizaci profilu, ukládání a načítání SharedPreferences. Import ani API sync nesmí přepisovat nesouvisející údaje.
- Přidej testy pro normalizaci tagu, duplicity, chybějící jméno, prázdné jméno a nesoulad tagů.

### Village export a catalogue
- Audituj parser podle skutečné struktury exportu. Ověř sekce Builders, Laboratory, Pets, Builder Base, Star Laboratory a další podporované kategorie.
- Zachovej stabilní game data ID jako autoritativní identifikátor. Neodvozuj názvy z nepodložených textů exportu.
- Deduplikuj pouze skutečné duplicity podle stabilního klíče; nikdy neslučuj dvě legitimní položky jen proto, že mají stejný název.
- Oprav chybějící položku v Laboratory a ověř Builder Base. Přidej fixture/testy pokrývající více současně aktivních položek.
- Zachovej neznámé ID explicitně jako unknown; nevymýšlej názvy, úrovně ani časy.
- Countdown musí vycházet z export timestampu a zbývajících sekund. Nevytvářej falešná procenta postupu bez známé celkové délky upgradu.

## 3. Widget: žádné svévolné ořezávání

Současný CocWidget.kt obsahuje pevné limity pro sekce — Builders/Laboratory/Builder Base mají omezený počet položek přes take(...), zatímco výška widgetu určuje další globální limit. To může způsobit, že se při velké ploše nezobrazí všechny relevantní upgrady.

- Odstraň hardcoded limity typu Builders=2, Laboratory=2, Builder Base=2, pokud nejsou odůvodněné datovým modelem.
- Velký widget musí využít dostupnou plochu a zobrazovat všechny položky, které se do ní vejdou.
- Nevynechávej položky tiše. Pokud se všechny nevejdou, ukaž indikátor dalších položek / jejich počtu a zvol stabilní prioritu.
- Layout musí být adaptivní podle skutečné šířky i výšky, ne jen podle několika křehkých prahů.
- Ověř kompaktní, střední, široký a maximální rozměr. Kompaktní widget může ukazovat výběr; velký widget musí být informačně bohatý.
- Zachovej hierarchii: identita hráče, Town Hall, sekce upgradů, položky, countdowny a stáří exportu.
- Dlouhé názvy, velká čísla, prázdné sekce a unknown položky nesmí rozbít layout.
- Nepoužívej nepodporované Glance API. Ověř skutečný build proti používané verzi Glance.
- Respektuj limity Glance widgetů: nepředpokládej běžný scroll jako u Activity. Pro další obsah použij vhodné stránkování nebo přehledný souhrn.

## 4. Vizuální redesign widgetu

Proveď skutečný redesign, ne jen změnu barev a paddingů.

Vizuální směr:
- prémiový Clash of Clans companion,
- tmavý teplý kamenný/wooden podklad, zlaté akcenty, krémová typografie a střídmé zelené zvýraznění countdownů,
- kvalitní herní artwork jako identifikace položek,
- jasná typografická hierarchie a čisté skupiny,
- přehledné countdowny a úrovně,
- konzistentní rohy, spacing a kontrast,
- žádné zbytečné emoji, dekorace nebo generické placeholdery.
- Zachovej čitelnost i na menším widgetu a respektuj systémové rozměry launcheru.

Design musí být konzistentní napříč velikostmi. Nenafukuj pouze stejný malý layout; navrhni pro různé velikosti vhodné kompozice.

## 5. Redesign hlavního rozhraní aplikace

Současný MainActivity.kt je jednoduchý dlouhý sloupec tlačítek a polí. Nahraď jej promyšleným, moderním rozhraním.

Požadavky:
- výrazná, ale ne přeplácaná hlavička s identitou COCwidgt,
- přehled profilu hráče a stavu propojení,
- samostatná a jasná sekce pro import village JSON,
- samostatná sekce API synchronizace a bezpečného nastavení tokenu,
- viditelný stav posledního importu/syncu a chybové stavy,
- jasné primární a sekundární akce,
- žádné opakované Player Tagy ani zbytečně dlouhé vysvětlující odstavce,
- ovládací prvky musí mít správné loading/disabled/error/success stavy,
- přístupnost: popisky, kontrast, touch targety, scroll a malé displeje,
- zachovej stávající funkce a zabezpečení tokenu.
- Nepřidávej zbytečné obrazovky nebo nové závislosti kvůli drobným efektům.

Aplikace má být widget-first: její hlavní účel je nastavit a importovat data pro widget. Rozhraní má působit jako kvalitní management companion, nikoli jako zbytečně velká aplikace.

## 6. Debloat a optimalizace APK

- Zkontroluj každou přímou i tranzitivní knihovnu a ověř její reálné použití.
- Odstraň pouze skutečně nepoužívané závislosti a redundantní knihovny.
- Neodstraňuj tranzitivní dependency ručně, pokud je vyžaduje jiná knihovna.
- Nepřidávej exclusion pravidla naslepo.
- Prověř Compose/Glance dependency graph, WorkManager, Lifecycle, Core a testovací knihovny.
- Zapni R8 minifikaci a resource shrinking pro release, pokud je to bezpečné; přidej jen nutná keep pravidla a ověř release runtime kritické cesty.
- Debug-only tooling nesmí být v release APK.
- Změř velikost APK před a po. Uveď rozdíl v bytech i procentech. Pokud baseline nelze reprodukovat, jasně to přiznej.
- Zachovej správné podepisování release. Nikdy neukládej keystore, hesla, API tokeny ani Base64 secret do Git.
- Neobětuj stabilitu kvůli umělému snížení velikosti APK.

## 7. Architektura a kompatibilita

- Zachovej současnou platformní architekturu; KMP teď nezaváděj.
- Omez zbytečné odpovědnosti v MainActivity, widgetu a parseru tam, kde to zlepší testovatelnost bez zbytečného přepisování.
- Odděl čistou datovou logiku od Android Context, pokud je to praktické a bezpečné.
- Nepřidávej abstrakce jen pro abstrakce.
- Zachovej minSdk, applicationId, namespace a kompatibilitu s daty uloženými v předchozí verzi.
- Neprováděj destruktivní migraci SharedPreferences bez migračního kódu.

## 8. Testy a akceptační kritéria

Přidej/rozšiř testy minimálně pro:
- Player Tag normalizaci a deduplikaci,
- validní a chybějící player name,
- vazbu profilu na správný tag,
- Builder Base více položek,
- Laboratory více položek včetně chybějícího případu,
- deduplikaci podle stabilního ID bez ztráty legitimních instancí,
- výpočet countdownu a stale export,
- prázdné a neznámé export entity,
- adaptivní výběr položek pro různé velikosti widgetu, pokud lze logiku extrahovat do čisté testovatelné funkce.

Před dokončením spusť:
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease

Dále:
- ověř, že release APK je správně podepsané, pokud jsou lokálně dostupné signing secrets,
- zkontroluj obsah APK a velikost,
- ověř, že minifikace neodstranila komponenty potřebné pro widget, WorkManager, JSON parsing nebo zabezpečení,
- zkontroluj výsledný diff a git status.

Pokud něco selže, oprav příčinu a testy opakuj. Neoznačuj build za úspěšný, pokud skutečně neproběhl.

## 9. Git workflow a release

- Pracuj pouze na codex/v0.5.0-debloat-redesign.
- Nedělej sérii malých PR. Cílem je jeden ucelený update.
- Po úspěšných kontrolách vytvoř jeden nebo několik logicky seskupených commitů na stejné větvi; žádné force-push.
- Pushni pracovní větev, pokud je GitHub autentizace dostupná.
- Nevytvářej tag, GitHub Release ani prerelease v0.5.0. Nejdřív musí uživatel ověřit redesign na telefonu.
- Nevydávej release s nepodepsaným APK a nikdy nepoužívej jiný signing key než existující release keystore.
- Neprováděj KMP migraci. Ta bude samostatný update po schválení v0.5.0.

## 10. Závěrečný report

Na konci uveď:
- co bylo opraveno a přepracováno,
- jaké závislosti byly odstraněny a proč,
- velikost APK před/po a rozdíl,
- výsledky všech testů, lint a buildů,
- zda byl release APK podepsán,
- commit SHA a název pracovní větve,
- známé limity a co má uživatel otestovat na telefonu.

Buď kritický. Pokud něco nebylo ověřeno, napiš to výslovně. Nezakrývej nedodělky a nevymýšlej výsledky.
