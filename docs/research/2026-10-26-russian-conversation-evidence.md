# Russische Gesprächsreise: Evidenz, Umsetzung und Grenzen

Recherchestand: 26.10.2026. Zielgruppe: deutschsprachiger erwachsener Anfänger mit einzelnen russischen Wörtern. Implementierung: Android/Compose + vorhandenes Fastify-Backend und lokales Chatmodell.

## Korrektur nach dem ersten Nutzertest

Die erste veröffentlichte Oberfläche wurde zu Recht als überfordernd kritisiert. Eine Stage-0-Anweisung an ein Sprachmodell ist **kein tatsächlicher Anfängerlehrgang**. Auch ein Quellenregister und eine Funktionsmatrix belegen weder gute UX noch funktionierende Vermittlung. Der alte Einstieg fragte nach Produktion, bevor der Nutzer den nötigen Wortschatz gelernt hatte.

Der überarbeitete Einstieg verwendet deshalb vier kuratierte Mini-Lektionen mit jeweils vier vorher erklärten Wörtern. Die Reihenfolge ist **Kennenlernen → gemeinsam mit Antwortmodell → selbst abrufen**. Partneräußerungen und akzeptierte Antworten werden automatisch gegen den bis dahin eingeführten Wortschatz geprüft. Unbekanntes Autoplay wurde entfernt; auch bestehende Einstellungen werden beim Upgrade auf manuelles Abspielen gesetzt. Die vorhandenen Lernstände bleiben erhalten. Die alte KI-Sitzung wird beim Upgrade separat lokal archiviert und nicht ungefragt fortgesetzt. Neue Starterlektionen beginnen auf der Startseite.

Ergänzende Primärquelle E18: Atkinson, Renkl & Merrill (2003), [Transitioning From Studying Examples to Solving Problems](https://doi.org/10.1037/0022-0663.95.4.774), [Abstract im Universitätsnachweis](https://experts.azregents.edu/en/publications/transitioning-from-studying-examples-to-solving-problems-effects-/). Die Arbeit untersucht den Übergang von ausgearbeiteten Beispielen zu selbstständigem Problemlösen. Schrittweise reduzierte Hilfen unterstützten Nahtransfer; mit Prinzipienhinweisen wurden in zwei Experimenten auch Verbesserungen beim Ferntransfer beobachtet. **Keine Russischstudie und keine direkte Evaluation dieses Dialogtrainings.** Die Übertragung auf einen angeleiteten Spracheinstieg ist eine begründete Gestaltungsentscheidung. Vier Wörter, drei Übungsphasen und die Freischaltung nach drei Mini-Lektionen sind Produktentscheidungen, keine wissenschaftlich ermittelten Optimalzahlen.

E1 bleibt die Grundlage für spätere Erinnerungsproben: eingeführte Wörter werden nach einer abgeschlossenen Lektion automatisch für frühestens den nächsten Tag gespeichert. Unmittelbares Nachsprechen wird nicht als selbstständiger Abruf gezählt. Auch ein richtig wiedergegebenes Wort belegt keine korrekte Aussprache: geprüft wird der vom Nutzer bestätigte ASR-Text.

Freie Gespräche verwenden den tatsächlich eingeführten Wortschatz als Kontext. Das Modell soll höchstens einen neuen lexikalischen Eintrag pro Anfängerturn einführen. Zusätzlich prüft der Server bei Stage 0 die konkreten Wortformen in Frage, Satzanfang, Wortliste und Antwortbeispielen: höchstens zwei noch nicht eingeführte Formen, alle mit Erklärung in der vorbereitenden Wortliste. Beugungen zählen vorsichtig als neue Form. Fehlende Abdeckung löst einen gezielten Neuversuch aus; eine weiterhin ungeeignete Ausgabe wird nicht angezeigt. Die App zeigt jede Frage mit deutscher Bedeutung und einer bewussten Vorbereitungsphase. Bestätigte neue Wörter werden als eingeführt gespeichert und in spätere Gespräche und Erinnerungsproben übernommen. Diese Grenze ist eine UX-Schutzregel, kein wissenschaftliches Optimum; eine korrekte Übersetzung oder gelungene Gesprächsführung lässt sich dadurch nicht beweisen.

Die nachfolgende ursprüngliche Funktionsmatrix dokumentiert den vorherigen Implementierungsumfang. Wo sie Autoplay, sofort verborgene Anfängerantworten oder einen rein promptbasierten Einstieg beschreibt, wird sie durch diese Korrektur ersetzt. Die Anzahl der Funktionen ist ausdrücklich kein Qualitätsnachweis.

## Ergebnis der Recherche

Die belastbarste praktische Kombination ist bedeutungsvolle Sprachverwendung, aktive Produktion, verständlicher Input mit verfügbarer Unterstützung, gezielte Korrektur mit eigenem Verbesserungsversuch und wiederholtes Abrufen über mehrere Tage. Interesse und ein druckarmer Rahmen unterstützen die Nutzung. **Keine recherchierte Studie beweist, dass diese konkrete App oder 50 einzelne Bedienfunktionen die weltweit schnellste Methode sind.** Die Prinzipien sind unterschiedlich gut gestützt; konkrete Stufen, Knöpfe und Zeitvorgaben sind daraus abgeleitete Produktentscheidungen.

Flüssiges Sprechen ist nicht dasselbe wie fehlerfreies Sprechen, ein bestimmtes CEFR-Niveau oder vollständiges Verstehen jedes Films/Liedes. Schnelle authentische Sprache, Akzente, kulturelle Anspielungen, Wortschatz und Musikmischung erfordern zusätzliche Erfahrungen. Ein synthetischer KI-Dialog trainiert nur einen Ausschnitt davon.

## Rechercheweg und Auswahl

Gezielte Web-Recherche nach Metaanalysen, systematischen Reviews und Primärstudien zu spacing/retrieval, oral corrective feedback, oral task repetition, captioned listening einschließlich Russian, ASR, GenAI, enjoyment und instructional intensity. Ergänzende Suche nach offiziellen Stundenrichtwerten. Ältere Grundlagen wurden mit Arbeiten aus 2024–2026 abgeglichen. Bevorzugt wurden Verlagsseiten, Universitätsarchive und Originalpublikationen; Blog-Ranglisten und kommerzielle „fluent in X days“-Versprechen wurden nicht als Belege verwendet.

Dies ist ein fokussierter Forschungsüberblick, **keine eigene PRISMA-Metaanalyse**. Abstract-only, Volltext und administrative Orientierung werden unten unterschieden. Effektstärken verschiedener Outcomes sind nicht direkt als Rangliste vergleichbar. Mehrere Reviews können dieselben Primärstudien enthalten; ihre Stichproben werden deshalb nicht addiert.

## Evidenzregister

| ID | Originalquelle und Zugriff | Ergebnis und Reichweite | Konsequenz für Hikari |
|---|---|---|---|
| E1 | Kim & Webb (2022), [The Effects of Spaced Practice on Second Language Learning](https://doi.org/10.1111/lang.12479). Verlagsabstract geprüft. | 48 Experimente, 98 Effektstärken, N=3.411. Mittelgroße bis große Spacing-Effekte; länger verteiltes Üben günstiger für verzögerte Tests. Gleiche und expandierende Intervalle statistisch vergleichbar. Keine optimale tägliche Stundenanzahl. | Persönliche Ausdrücke an späteren Tagen abrufen; nicht nur unmittelbar nachsprechen. Exakte Intervalle bleiben Heuristik. |
| E2 | Carpenter, Pan & Butler (2022), [The science of effective learning with spacing and retrieval practice](https://www.nature.com/articles/s44159-022-00089-1). Review-Abstract geprüft; Volltextzugriff blockiert. | Domänenübergreifender Überblick zu verteilter Übung, Abruf und Metakognition. Kein Russisch-spezifischer Geschwindigkeitsfaktor. | Erst eigene Antwort, Lösungen auf Anfrage, Rückblick auf tatsächliche Abrufe statt nur gefühlte Vertrautheit. |
| E3 | Lyster & Saito (2010), [Oral Feedback in Classroom SLA](https://doi.org/10.1017/S0272263109990520). Verlagsabstract geprüft. | 15 Unterrichtsstudien, N=827. Korrektives Feedback hatte anhaltende positive Effekte; Prompts stärker als bloße Reformulierungen, besonders bei frei konstruierten Antworten. Kein Beleg für beliebige LLM-Korrektheit. | Erst gezielter Hinweis und Selbstkorrektur; Modellantwort optional. Höchstens ein Fokus pro Antwort ist eine Belastungsentscheidung. |
| E4 | [Task repetition and L2 oral performance: A meta-analysis](https://doi.org/10.1016/j.system.2025.103868) (2025), Verlagsabstract/indexierter Verlagstext; Direktzugriff teilweise 403. | Positive, nach Komplexität, Genauigkeit und Flüssigkeit unterschiedliche Ergebnisse. Kein Nachweis, dass identische Übung automatisch auf alle neuen Gespräche überträgt. | Gleiche Szene erneut durchspielen, zusätzlich neue Situationen und Rollenwechsel. Keine erfundene feste Wiederholungszahl als Optimum. |
| E5 | Hanzawa et al. (2026), [A scoping review of oral task repetition](https://doi.org/10.1016/j.rmal.2025.100292). Verlagsabstract geprüft. | Überblick über 74 empirische Studien; heterogene Begriffe, Designs und Zielgrößen. Scoping Review ist kein gepoolter Wirksamkeitsbeweis. | „Flüssigkeit“ nicht aus einem Textscore ableiten; Wiederholung und Transfer unterscheiden. |
| E6 | Montero Perez, Van Den Noortgate & Desmet (2013), [Captioned video for L2 listening and vocabulary learning](https://doi.org/10.1016/j.system.2013.07.013). [Universitätsnachweis](https://biblio.ugent.be/publication/8673620), Verlagsabstract; Volltext institutionell beschränkt. | Metaanalyse zugunsten von Captions für Hörverstehen und Wortlernen. Das ist Video-Evidenz, kein direkter Test der hier verwendeten TTS-Chats. | Partnerfrage hörbar und lesbar; Text kann ausgeblendet werden. Kein pauschales „Text immer schlecht“. |
| E7 | Winke, Gass & Sydorenko (2010), [The effects of captioning videos used for foreign language listening activities](https://www.lltjournal.org/item/10125-44203/). Original-PDF, Methoden/Ergebnisse geprüft. | N=150, darunter 41 Russischlernende im 2./4. Studienjahr. Captioning unterstützte Verständnis/Worterkennung. Vorteile der Reihenfolge differenzierten; kleine unbalancierte Sprachgruppen, keine absoluten Anfänger. | Anjas Text ist standardmäßig sichtbar, mögliche **Antworten** sind verborgen. Nur-Hören ist freiwillig. Übertragung auf Anfänger und TTS ausdrücklich vorsichtig. |
| E8 | Ngo, Chen & Lai (2024), [ASR in ESL/EFL pronunciation: A meta-analysis](https://doi.org/10.1017/S0958344023000113). Verlagsvolltext geprüft. | 15 Studien, 38 Effektstärken, g=0,69 insgesamt. Explizites Feedback, Dauer und Zielmerkmale moderieren. Überwiegend Englisch; ASR-Transkription ist keine valide vollständige Akzentdiagnose. | Bestätigbares Transkript, Erkennungsfehler korrigierbar, Feedback nur zu Inhalt/Wortwahl. Keine falschen Aussprache-Prozente. |
| E9 | [Aggregating the evidence of automatic speech recognition research claims in CALL](https://doi.org/10.1016/j.system.2024.103250) (2024), Verlagsabstract geprüft. | Synthese von 50 Studien mit stark variierenden Designs und Evidenzstärken. Einzelne positive Studien erlauben keine unbegrenzte Extrapolation. | KI-Ausgabe als Rückmeldung kennzeichnen, nicht als normiertes Sprachniveau. |
| E10 | Uchihara, Karas & Thomson (2024), [Does perceptual HVPT improve L2 speech production?](https://doi.org/10.1017/S0142716424000195). Volltext/Abstract geprüft. | 31 Studien; kleine bis mittlere Produktionseffekte. Keine starke Evidenz für dauerhafte Retention/Generalisierung auf ungeübte Stimuli. | Eine einzige synthetische Stimme nicht als vollständiges Hörtraining verkaufen. Vorhandenes Lautlabor bleibt gesondert; kein neu behauptetes HVPT-Feature. |
| E11 | Botes, Dewaele & Greiff (2022), [Taking stock: foreign language enjoyment](https://doi.org/10.14746/ssllt.2022.12.2.3). Original-Journalabstract geprüft. | 96 Effektstärken, N=28.166; moderate positive Zusammenhänge mit Leistung und Kommunikationsbereitschaft. **Korrelation, kein Nachweis „Emotion ist allem überlegen“.** | Interessen, Stimmung, Geschichten, freie Entscheidungen und freiwillige Hilfen; keine Bestrafung für Fehler. |
| E12 | Saarela, Gunasekara & Kumarage (2026), [GenAI effects on language proficiency and affective–cognitive outcomes](https://doi.org/10.1007/s10791-026-10015-1). Offener Verlagsvolltext einschließlich Grenzen geprüft. | 51 Studien, 175 Effektstärken, überwiegend Englisch/Erwachsene, häufig kurzfristige oder selbstberichtete Ergebnisse und hohe Heterogenität. Positive Durchschnittseffekte beweisen nicht die Qualität eines konkreten lokalen Modells. | Kontextuelle KI-Gespräche mit überprüfbarer Struktur, klaren Fehlerzuständen und sichtbarer Rückmeldungsgrenze. Lokaler Modelltest bleibt zusätzlich erforderlich. |
| E13 | [Voice-based AI chatbots in English learning: meta-synthesis 2010–2024](https://doi.org/10.1017/S0958344024000168), ReCALL (online 2024). Verlagsabstract/Diskussion geprüft. | Häufige Probleme: nicht erkannte Lerneräußerungen, unnatürliche Gespräche, fehlendes explizites Feedback; keine Garantie gegenüber menschlichen Partnern. | Mikrofon nur bewusst starten; Text vor Senden prüfen; inhaltliche Rückfragen, spezifisches Feedback und Tippalternative. |
| E14 | [The Distributed Practice Effect on Classroom Learning](https://doi.org/10.3390/bs15060771) (2025). Originalabstract über PMC/Verlag geprüft; Direktzugriffe zeitweise beschränkt. | 22 Berichte, 31 Effekte, N>3.000, d=0,54, 95%-KI [0,31; 0,77]. Unterrichtsübergreifende Evidenz, keine universelle Tagesdosis. | Mehrere frei wählbare Blöcke, zeitversetzter Abruf; weder 10-Minuten-Dogma noch erfundene 10-Stunden-Abschaltgrenze. |
| E15 | Lu & Ke (27.07.2026), [Effects of Learning Context and Program Length on L2 Skill Development](https://doi.org/10.1111/modl.70085). Offener Volltext, Abstract/Methoden geprüft. | 725 Chinesischlernende, 12 Institutionen, überwiegend Studierende, im Mittel ungefähr intermediate. Intensive immersive Sommerprogramme konnten wirksam sein; kein randomisierter Test von 10 täglichen Stunden bei Russisch-Anfängern. | „Intensiv ist immer ineffizient“ wäre falsch. Zeitplan vermeidet automatische Effizienzabschläge und berücksichtigt Studienkontext. |
| E16 | FSI, [Foreign Language Training, offizielles Archiv 2021–2025](https://2021-2025.state.gov/foreign-language-training). Offizieller indexierter Text; Direktzugriff zeitweise technisch gesperrt. | Russisch ca. 44 Wochen/1.100 **Unterrichtsstunden**, Ziel ILR Speaking-3/Reading-3, Erfahrungen mit US-Diplomaten. Administrative Orientierung, keine kontrollierte Lernstudie und kein deutschsprachiger App-Richtwert. Aktuelle Hauptseite 403; deshalb ausdrücklich archivierter Wert. | Nur beschrifteter Vergleich, nicht persönliche „erforderliche Stunden“. Keine Umrechnung von App-Minuten in dieses Ziel. |
| E17 | van Zeeland & Schmitt (2013), [Lexical Coverage in L1 and L2 Listening Comprehension](https://academic.oup.com/applij/article-pdf/34/4/457/14093926/ams074.pdf). Originalabstract geprüft. | Lexikalische Abdeckung und Verständnis hängen zusammen; Befunde beziehen sich auf untersuchte englische Hörtexte. Selbst hohe Abdeckung bedeutet nicht vollständiges Verstehen. | Keine russische Wortanzahl-Garantie und kein „alle Filme nach X Wörtern“. |

## Wie der Zeitplaner ehrlich rechnet

Eingaben: Wunschdatum, geplante Lerntage/Woche, selbst gewähltes Tagesbudget und Blocklänge. Die Verteilung rechnet in Wochen ab heute; verbleibende Tage werden bis zur gewünschten Zahl von Lerntagen berücksichtigt. Es ist kein Terminplan mit festgelegten Wochentagen.

- `Lerntage = volle Wochen × Tage/Woche + min(Resttage, Tage/Woche)`.
- `Eigenes Stundenbudget = Lerntage × Minuten/Tag / 60`.
- `FSI-Vergleich pro Lerntag = 1.100 / Lerntage` — ausdrücklich keine persönliche Empfehlung.
- `Blöcke = aufgerundet(Minuten/Tag / gewählte Blockminuten)`; letzter Block kann kürzer sein, Pausen zusätzlich.

Beispiel: 14 Tage, 5 Lerntage/Woche und 30 Minuten/Tag ergeben 10 Übungstage und 5 Stunden Budget. Die Division des historischen FSI-Pensums ergäbe 110 Unterrichtsstunden/Übungstag: rechnerisch unmöglich, nicht ein Lernvorschlag. Ein Jahr mit täglich einer Stunde ist ein anderes Zeitbudget; daraus folgt kein garantierter Spracherfolg.

Es gibt absichtlich keinen „Gehirn schaltet nach N Stunden ab“-Faktor, keine automatische Prozent-Effizienz und keine behauptete ideale Blocklänge. E1/E14 betreffen Verteilung; E15 zeigt, warum das nicht mit „jede intensive Ausbildung schlecht“ gleichgesetzt werden darf. Gemessene aktive Gesprächszeit und späteres Erinnern zeigen den individuellen Verlauf, liefern aber noch keine validierte Fließend-Prognose.

## Automatische Progression

Alle neuen Nutzer beginnen auf Stufe 0 „Erste Wörter“. Stufen sind Aufgabenunterstützung, keine A1–C2-Zertifikate. Mehr Unterstützung nach drei schwierigen/unterstützten Antworten ist jederzeit möglich, ohne gespeicherten Fortschritt zu löschen.

Aufstieg höchstens eine Stufe: mindestens 15 Beobachtungen auf der Stufe; unter den letzten 20 mindestens 12 inhaltlich klare, selbstständig gesprochene Antworten; mindestens drei Tage, zwei Situationen und acht unterschiedliche Antworttexte; zusätzlich mindestens zwei weitere an späteren Tagen erinnerte Ausdrücke je Stufe. Wiederholte Netzwerkantworten bekommen nicht erneut Fortschritt; Selbstkorrekturen sind unterstützte Übung, keine neue unabhängige Evidenz. Textbearbeitung nach ASR zählt konservativ als getippt.

**Diese Schwellen sind transparente, konservative Produktheuristiken, nicht aus einer Studie extrahierte Normwerte.** LLM-Urteile und ASR bleiben fehleranfällig. Der lokale Fortschritt ist weder ein klinischer Test noch eine Messung des gesamten Russischkönnens.

## Eingebaute Funktionsmatrix

E = Literaturstütze für das Lernprinzip; U = konkrete Umsetzung/Bedienhilfe, deren eigene Wirksamkeit nicht separat nachgewiesen ist. „Implementiert“ heißt vorhandener ausführbarer Codepfad, nicht automatisch ein menschlich geprüfter Langzeit-Lerneffekt. Szenariovarianten werden zusammen als eine Funktion gezählt.

| # | Funktion | Codepfad / Bedienung | Evidenz |
|---|---|---|---|
| 1 | Einstieg mit einzelnen Wörtern | Stage 0, Backend-STAGES | U; E12 |
| 2 | Automatischer Aufstieg anhand mehrtägiger Leistung | `RuChatLearning.record/readyToAdvance` | E1/E2; Schwellen U |
| 3 | Selbstständigkeit statt bloßer Lösungsanzeige erfassen | `helped/independent` | E2; U |
| 4 | Situationsvielfalt in der Fortschrittsentscheidung | zwei Szenarien/eigene Antwortvariation | E4/E5; U |
| 5 | Bei Schwierigkeiten wieder stärker stützen | `supportStage` | U |
| 6 | Acht lebensnahe Szenarien auswählen | Setup/Szenariokarten | E11/E12; U |
| 7 | Eigene Interessen eingeben | Setup/interest → Gesprächskontext | E11; U |
| 8 | Gesprächsstimmung wählen | calm/playful/curious → Prompt | E11; U |
| 9 | Alltag, Geschichten oder Medien als Schwerpunkt | goal → Prompt | E12; U |
| 10 | Kurze oder längere Gesprächsziele | 6/12/24 eigene Antworten | U |
| 11 | Zusammenhängender Gesprächskontext | geordnete history bis Kontextgrenze | E12/E13; U |
| 12 | Auf persönliche Antworten inhaltlich reagieren | freier KI-Dialog, keine Antwortliste als Prüfschlüssel | E3/E12 |
| 13 | Tiefer nachfragen | `deeper` | E12; U |
| 14 | Kleine überraschende Wendung | `twist` | E11; U |
| 15 | Rollen wechseln: selbst fragen | `roles` | E12; U |
| 16 | Aus dem Gedächtnis nacherzählen | `retell` | E2/E4; U |
| 17 | Kurze Hörszene plus Verständnisfrage | `listen`, eigens erzeugter Inhalt | E6/E7; TTS-Transfer U |
| 18 | Frage einfacher formulieren lassen | `simplify` | U |
| 19 | Eigene freie Antwort per Mikrofon | Android-ASR → bestätigter Text | E8/E13 |
| 20 | Erkanntes Transkript vor Absenden prüfen/ändern | Eingabefeld | E8/E13; U |
| 21 | Längere Antworten stückweise diktieren | Weiter diktieren | U |
| 22 | Getippte Antwort als Alternative | freie Texteingabe | U |
| 23 | Antworten standardmäßig ohne Auswahl abrufen | keine examples vor Stufe 4 der Hilfe | E2 |
| 24 | Semantischer Tipp | Hilfe 1 | E3; U |
| 25 | Passende Wörter mit Bedeutung | Hilfe 2 | E3/E6; U |
| 26 | Unvollständiger Satzanfang | Hilfe 3 | E3; U |
| 27 | Unterschiedliche Beispielantworten erst auf Wunsch | Hilfe 4 | E2/E3; U |
| 28 | Beispielantwort anhören | Hilfe/Anhören | E6; U |
| 29 | Modellfrage vorlesen | Android-TTS | E6/E7; U |
| 30 | Gehörtes beliebig wiederholen | Anhören | E4/E6; U |
| 31 | Langsameres Abspielen | Langsamer/Tempoeinstellung | U |
| 32 | Mitlesen oder bewusst nur hören | sichtbarer Fragetext, optional audioFirst | E6/E7 |
| 33 | Deutsche Bedeutungsstütze auf Anfrage | Deutsch | E6; U |
| 34 | Inhaltliche Verständlichkeit rückmelden | clear/partial/unclear, kein Akzentscore | E3/E8 |
| 35 | Ein konkreter Korrekturfokus | Feedback/focus | E3; Dosierung U |
| 36 | Hinweis vor fertiger Korrektur | repairCue | E3 |
| 37 | Eigenen Verbesserungsversuch abgeben | Selbst verbessern/repair | E3/E4 |
| 38 | Verbesserte Formulierung aufdecken | correctedRu | E3 |
| 39 | Deutsche Erklärung zur Verbesserung | explanationDe | E3; U |
| 40 | Verbesserte Formulierung anhören | Verbesserung anhören | E3/E8; U |
| 41 | Persönliche nützliche Ausdrücke speichern | chunk/merken | E1/E2; U |
| 42 | Fällige Wiederholung an späteren Tagen | `due`/Merken | E1/E14 |
| 43 | Russischen Ausdruck zuerst aus dem Kopf abrufen | deutsche Karte, russische Lösung verborgen | E2 |
| 44 | Gesprochener Erinnerungsversuch mit Textabgleich | Review/Prüfen | E2/E8; U |
| 45 | Wiederholungsabstände anhand des Abrufs ändern | `grade`, 1/2/4/7/14/30 Tage | E1; Intervalle U |
| 46 | Bei Vergessen früher zurückkehren | Rücksetzung der Kartenbox | E1; U |
| 47 | Fällige Ausdrücke in neuen Gesprächen aufgreifen | request.review → Prompt | E1/E4; U |
| 48 | Persönlichen Sprachschatz nachschlagen | geschlossenes Archiv, als Lösungshilfe markiert | U |
| 49 | Gespräch und Entwurf später fortsetzen | lokale Sessionpersistenz | U |
| 50 | Dieselbe Szene erneut spielen | Rückblick/Gleiche Szene | E4/E5 |
| 51 | Rückblick auf eigene, gesprochene und unterstützte Antworten | Summary | E2; U |
| 52 | Drei nützliche Ausdrücke aus dem Gespräch festhalten | Summary/merken | E1/E2; U |
| 53 | Selbstsicherheit vorher/nachher reflektieren | Confidence, getrennt von Leistung | E11; U |
| 54 | Tagesziel freiwillig beenden oder weiterreden | Antworten-Ziel/Mein Rückblick | E11; U |
| 55 | Pausenhinweis nach selbst gewähltem Block | aktive Zeit/Blocklänge | E1/E14; U |
| 56 | Wunschdatum eingeben | Plan | U |
| 57 | Lerntage pro Woche planen | Plan/Kalenderarithmetik | E1/E14; U |
| 58 | Persönliches Tageszeitbudget festlegen | Plan | U |
| 59 | Aufteilung in frei wählbare Blöcke | Plan/Blockrechnung | E1/E14; U |
| 60 | Historischen Stundenrichtwert transparent vergleichen | FSI-Referenz und Budgetabweichung | E16, administrativ |
| 61 | Tatsächliche aktive Gesprächszeit heute und über 7 Tage verfolgen | activeSecondsByDay | U |
| 62 | Späteres Erinnern und letzte selbstständige Antworten anzeigen | Plan/Beobachtungen | E1/E2; U |
| 63 | Wissenschaftliche Gründe direkt in der App nachlesen | Warum so?/Originalquellen | Transparenz, U |

## Technik und überprüfbare Grenzen

Keine zusätzlichen Laufzeitbibliotheken oder Datenbankmigrationen. Android speichert kleine Fortschritts-/Sitzungsobjekte in separaten SharedPreferences. Bestehende Lektionen, Satzbuch und Lautlabor bleiben verfügbar. Das Backend erhält bestätigte Texte und die begrenzte Unterhaltung; es legt dafür keine dauerhafte Gesprächsdatenbank an. Androids gewählter Spracherkennungsdienst kann Audio online verarbeiten. Der konfigurierte KI-Server erhält den Gesprächskontext. Keine Behauptung, dass alle Audioverarbeitung offline stattfindet.

`GET /russian/conversation/status`, `POST /russian/conversation/turn`. Modellanschluss: `RUSSIAN_CHAT_URL`/`RUSSIAN_CHAT_MODEL`, sonst vorhandene LM-Studio-Konfiguration. Keine Cloud-Ausweichantwort und keine fiktiven Bewertungen bei Serverfehlern. Maximal zwei parallele Modellaufrufe, begrenzte Eingaben/Antworten, Timeout und strukturierte Validierung. Nutzertext steht nicht im Systemprompt. Prompt-Isolation reduziert Instruktionskonflikte, garantiert aber keine vollständige Prompt-Injection-Abwehr.

Die Gesprächsreise verwendet kontextbezogene Szenariokarten und vorhandene Icons, **keine neuen Stockbilder**. Deshalb werden weder ungeprüfte Bilder eingebettet noch Lizenz-/Bildprüfungen vorgetäuscht.

Eine noch durchzuführende Lernwirksamkeitsprüfung müsste verblindete menschliche Bewertungen, neue Gesprächspartner/Themen, verzögerte Retention, authentisches Hörmaterial und Zeitaufwand erfassen. Code-Tests prüfen Rechenregeln und Fehlerfälle, nicht den wissenschaftlichen Beweis maximaler Lerngeschwindigkeit.


## Technische Prüfung am 26.10.2026

- Backend: TypeScript-Build erfolgreich; 13 Vitest-Tests bestanden. Geprüft sind Eingabegrenzen, Rollentrennung, russische Textfelder, Modellwahl, ungültige/abgeschnittene Antworten, ein begrenzter Wiederholungsversuch mit gemeinsamer Deadline, native Ausgabe ohne Denktext, HTTP-Vertrag, Parallelitätsgrenze und Freigabe nach Fehlern.
- Android: 10 JUnit-Tests bestanden (0 Fehler, 0 übersprungen). Abgedeckt sind Anfängerstart, mehrtägiger Aufstieg, Hilfe/Tippen, Vielfalt, spätere Erinnerung, Duplikate, Wiederholungsintervalle, Kalenderarithmetik, ungültige Eingaben und Persistenzserialisierung.
- `:app:assembleDebug` erfolgreich. APK-ZIP vollständig lesbar; 2.594 russische Asset-Einträge; APK-Signatur v2 verifiziert. Datei: `android/app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `5e8f5458a580ceb7d043bb6564c56f798382f0d1154746a05420bbeca8b55394`. Dies ist eine Debug-Testversion, kein veröffentlichtes Release.
- Die Android-Oberfläche sowie echtes Mikrofon/TTS sind **noch nicht am Gerät visuell bzw. akustisch verifiziert**. Der vorhandene Emulator antwortete auch nach Kaltstart nicht zuverlässig auf Android-Befehle; der Installationsversuch wurde nicht erfolgreich bestätigt. Die verfügbare UI-Steuerung konnte sein Fenster nicht auswählen. Deshalb wird keine erfolgreiche Handyinstallation behauptet.
- Der bestehende iCloud-Zustand blockierte zunächst Build- und Asset-Zugriffe. Die vorhandenen Audiodateien wurden nachgeladen; keine Audiodateien wurden inhaltlich geändert. Der alte generierte Build bleibt unter `android/.gradle/app-build-before-conversation` gesichert.

### Lokaler Modellbetrieb

Für diese Installation ist in der nicht versionierten `backend/.env` ausschließlich für den neuen Modus `RUSSIAN_CHAT_URL=http://localhost:1234`, `RUSSIAN_CHAT_MODEL=google/gemma-4-31b` und `RUSSIAN_CHAT_API=native` gesetzt. Bestehende allgemeine LM-Studio-Modellwerte bleiben erhalten. Der native [LM-Studio-Chat-Endpunkt](https://lmstudio.ai/docs/developer/rest/chat) wird mit `reasoning: off` und `store: false` verwendet; nur vollständige, validierte Nachrichten gelangen zur App. Bis zu zwei Generierungsversuche teilen sich ein 50-Sekunden-Limit. Server-/Modellprotokollierung außerhalb Hikari wird dadurch nicht pauschal ausgeschlossen.

Der optionale kompatible Anschluss (`RUSSIAN_CHAT_API` nicht `native`) verwendet JSON-Schema. Für [LM-Studio-Fehler #1773](https://github.com/lmstudio-ai/lmstudio-bug-tracker/issues/1773) wird fehlplatziertes JSON ausschließlich dann akzeptiert, wenn die gesamte Zeichenfolge direkt als vollständiges Antwortobjekt validiert; freier Denktext wird verworfen. Tests mit kleineren/schnelleren lokalen Modellen zeigten trotz gültigen JSON sprachliche Ungenauigkeiten. Die Modellwahl und die strukturelle Validierung sind daher keine Garantie fehlerfreier Lehrinhalte.


Vier zusammenhängende reale HTTP-Aufrufe mit Gemma 4 31B/native bestanden: Anfängerstart, freie Einwortantwort `Чай`, absichtlicher Fehler `Я любить чай` und eine Folgefrage. Alle vier HTTP 200, vollständige validierte Antwortobjekte; Dauer ungefähr 9,9 / 12,4 / 16,1 / 10,6 Sekunden. Die fehlerhafte Verbform wurde zu `Я люблю чай` verbessert, mit deutscher Erklärung und getrenntem Selbstkorrekturhinweis. Eine Einwortantwort wurde akzeptiert. Beobachtete Grenze: Folgefragen können sich wiederholen; der Test belegt keine fehlerfreie Pädagogik oder dauerhafte Lernwirkung.


Zwei weitere reale HTTP-Tests bestanden: Der Partner erinnerte sich auf Übungsstufe 2 korrekt an den erfundenen Katzennamen **Луна** (21,7 s); auf Stufe 3 erzeugte er eine längere originale Hörszene über zwei Freunde im Konzertsaal mit deutscher Bedeutung, Hilfen und einer Gefühlsfrage (30,5 s). Alle **sechs** abschließenden Aufrufe waren erfolgreich. Die synthetischen Testausgaben stehen in [russian-conversation-smoke-2026-10-26.json](./russian-conversation-smoke-2026-10-26.json). Backend-Health und Gesprächsmodellstatus lieferten abschließend HTTP 200; die Datenbank meldete gesund.

Noch erforderlich für eine Gerätefreigabe: Gesprächsreise öffnen, eine Aufnahme freigeben und verwerfen, einen erkannten Text bestätigen/korrigieren, Hilfeleiter/Audio/Wiederholung bedienen, App während KI-Anfrage in den Hintergrund schicken und später fortsetzen. Insbesondere Sprachdienst- und TTS-Verhalten hängen vom Android-Gerät ab. Diese ausstehende Prüfung wird nicht durch den APK-Build oder die Unit-Tests ersetzt.


## Prüfung des überarbeiteten Einstiegs (v0.89.44)

- Android: 341 JVM-Tests bestanden, einschließlich Wortschatzabdeckung aller Starterdialoge, Phasenfolge, falscher Antworten, Hilfen/selbstständiger Abrufe, Speicherung für morgen und Upgrade bestehender Lernstände. Die vorhandene Sitzung wird vor dem Wechsel lokal archiviert.
- Backend: 778 Tests bestanden; der vorhandene Test mit dem echten lokalen Aussprache-Erkenner lief mit Zugriff auf dessen Python-Cache ebenfalls erfolgreich. Die neue Gesprächsprüfung bewertet weiterhin Text, nicht Audio.
- 15 Paparazzi-Renderfälle der produktiven Compose-Komponenten: Einstieg, Wortkarte, gemeinsamer Versuch, eigener Versuch, Erfolg, Wiederholversuch, schmale 320-dp-Darstellung mit 130 % Schrift, breite Darstellung, Abschluss, freies Setup, Wortvorbereitung, Lernplan und Illustrationsübersicht. Die PNG-Dateien liegen unter `android/app/src/test/snapshots/images/`.
- Visuelle Korrekturen aus den Renderbildern: keine umgebrochene Phasenbeschriftung bei großer Schrift, kleinere dekorative Illustration in schmaler Ansicht, Wort und Bedeutung vor weiterer Erläuterung, Antworttext vor der Starthilfe, konsistente Farben auch bei Auswahlchips. Scrollbare Inhalte und feste Hauptaktion.
- Echter lokaler Modelltest über zwei aufeinanderfolgende Dialogzüge mit eingeführtem Wortschatz: Frage „Ты любишь кофе?“ mit Erklärung von „любишь“, anschließend „А ты любишь чай?“ mit Vorbereitung von „не“ für eine mögliche negative Antwort. Insgesamt rund 40 Sekunden für beide Antworten. Rohantworten ohne personenbezogene Daten: `russian-journey-ux-smoke-2026-10-26.json`.
- Weil das Modell häufig geläufige Formen aus der Wortliste ausließ, ergänzt ein kleiner redaktionell festgelegter Glossar solche bekannten Fälle, etwa „не — nicht“. Beliebige unbekannte Wörter werden nicht automatisch übersetzt oder erraten. Die anschließende Abdeckungsprüfung gilt trotzdem.

**Prüfgrenze:** Die Renderfälle sind echte Android-Compose-Layouts, keine Web-Mockups, aber kein vollständiger Gerätetest. Android-Mikrofonberechtigung, Spracherkennungsdienst, russische TTS-Stimme, Tastatur und tatsächliches Audio auf dem Nutzergerät bleiben separat zu prüfen. Ein Screenshot-Test ersetzt weder Interaktionstests am Gerät noch eine Lernwirksamkeitsstudie.


Nachprüfung am echten HTTP-Endpunkt: Ein vollständig bekannter Ein-Wort-Dialog darf eine leere neue Wortliste und keinen Satzanfang benötigen. Der reale Aufruf deckte diese unnötige Schemaanforderung auf; sie wurde vor der APK-Auslieferung entfernt und mit einem zusätzlichen Backend-Test sowie einem eigenen Android-Renderfall abgesichert. Der erneut geprüfte App-Aufruf antwortete mit HTTP 200 in etwa 14 Sekunden. Fehlende neue Vokabeln sind damit kein Fehler mehr; nicht erklärte tatsächlich neue Formen werden weiterhin geprüft.
