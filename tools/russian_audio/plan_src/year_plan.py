"""Jahresplan des Sprechkurses: 365 Plan-Tage in 73 Blöcken zu je fünf Tagen
(vier Lektionen, dann ein Gesprächstag ohne neuen Stoff).

Gebaut sind die Blöcke 1–8 (Plan-Tage 1–40 = Lektionen 1–32 in course.json).
Alles danach ist Vorplanung: Titel und Lernziel je Lektion. Wird ein Block gebaut,
kommen seine Lektionen nach course.json und BUILT_BLOCKS wird erhöht — siehe README,
Abschnitt »Neue Kurstage bauen«.

Jeder Eintrag in BLOCKS:
  (Blocktitel, Schwerpunkt, [(Lektionstitel, Lernziel) × 4], (Gesprächstag, Szene))
Für gebaute Blöcke stehen Titel und Ziel in course.json; hier bleibt die Liste leer.
"""

BUILT_BLOCKS = 8

PHASES = [
    # (id, Titel, Zielniveau, Blöcke von–bis, worum es geht)
    ("p1", "Fundament", "A1", 1, 8, "Small Talk sicher führen und erste ehrliche Gespräche über dich, deinen Alltag und das, was dich bewegt."),
    ("p2", "Alltag ohne Stocken", "A2", 9, 20, "Jede Alltagssituation meistern. Die sechs Fälle kommen der Reihe nach dazu, jeweils an einem Thema, das du sofort brauchst."),
    ("p3", "Erzählen und Verstehen", "A2+", 21, 34, "Zusammenhängend erzählen: Vergangenheit, Zukunft, Bewegung, Vergleiche. Du verstehst längere Antworten und hakst nach."),
    ("p4", "Meinung und Tiefe", "B1", 35, 50, "Standpunkte begründen, über Psychologie, Beziehungen, Gesundheit und Business diskutieren, auch wenn es kompliziert wird."),
    ("p5", "Fließend und nuanciert", "B1+", 51, 66, "Klingen wie jemand, der dort lebt: Partikeln, Umgangssprache, Redewendungen, Humor, Kultur."),
    ("p6", "Souverän", "B2", 67, 73, "Argumentieren, beruflich bestehen und die wirklich wichtigen Gespräche führen. Am Ende steht das Jahresgespräch."),
]

# Satzbau-Muster, das in der Lektion gelernt wird (Etappe »Anwenden«).
PATTERN_OF_LESSON = {
    2: "p01_hochu", 3: "p03_chuvstvuyu", 4: "p15_u_menya", 5: "p09_ne", 6: "p06_nuzhno", 7: "p10_voprosy",
    8: "p02_nravitsya", 9: "p13_sravnenie", 10: "p08_budushchee", 11: "p18_esli", 12: "p05_kazhetsya",
    13: "p04_potomu_chto", 14: "p07_proshloe", 15: "p21_chuvstvuyu", 16: "p22_psihologiya", 17: "p23_pitanie",
    18: "p24_biznes", 19: "p25_glubokiy_vopros", 20: "p20_vazhno",
    21: "p11_povelitelnoe", 22: "p14_nuzhen", 25: "p16_pomnyu", 26: "p17_kogda", 28: "p12_by", 29: "p19_chtoby",
}

BLOCKS = [
    # ── Phase 1 · Fundament (gebaut) ────────────────────────────────────────
    ("Erste Worte", "Grüßen, vorstellen, erste Fragen", [],
     ("Erstes Kennenlernen", "Du triffst Anja zum ersten Mal: begrüßen, vorstellen, fragen, wie es geht und wie alt sie ist.")),
    ("Alltag und Menschen", "Nachfragen, Arbeit, Familie, Hobbys", [],
     ("Kaffee mit Anja", "Ihr sitzt im Café und redet über Arbeit, Familie und das, was ihr gern macht.")),
    ("Verabreden und mitreden", "Essen, Zeit, Treffen, Meinung", [],
     ("Den Abend planen", "Ihr macht aus, wann und wo ihr euch trefft, bestellt etwas und tauscht eure Meinung aus.")),
    ("Reagieren und ehrlich sein", "Gefühle zeigen, Abschied, innere Zustände", [],
     ("Wie es dir wirklich geht", "Anja merkt, dass dich etwas beschäftigt. Du erzählst ehrlich und reagierst auf das, was sie sagt.")),
    ("Dein Leben erklären", "Körper, Arbeit, Tagesablauf, Werte", [],
     ("Was dich ausmacht", "Ein langer Spaziergang: Ernährung, Arbeit, dein Tag und das, was dir im Leben wichtig ist.")),
    ("Unterwegs und im Alltag handeln", "Bitten, brauchen, bestellen, telefonieren", [],
     ("Ein Tag in der Stadt", "Du findest den Weg, kaufst ein, reservierst einen Tisch und sagst Anja am Telefon Bescheid.")),
    ("Erzählen und Erinnern", "Vergangenheit, Herkunft, Reisen, Pläne", [],
     ("Deine Geschichte", "Anja will alles wissen: wo du herkommst, was du erlebt hast und wovon du träumst.")),
    ("Nähe und Beziehung", "Zuneigung, Konflikt, Gesundheit, Bilanz", [],
     ("Abschlussgespräch der ersten Phase", "Ein Abend zu zweit: Du sagst, was sie dir bedeutet, klärst ein Missverständnis und erzählst, wer du bist.")),

    # ── Phase 2 · Alltag ohne Stocken (A2) ──────────────────────────────────
    ("Wohnen und Zuhause", "Präpositiv: wo etwas ist", [
        ("Meine Wohnung", "Du beschreibst, wo und wie du wohnst."),
        ("Zimmer und Möbel", "Du sagst, wo etwas steht, liegt oder hängt."),
        ("Nachbarn und Hausregeln", "Du klärst Alltägliches im Haus: Lärm, Müll, Schlüssel."),
        ("Umziehen und Einrichten", "Du erzählst vom Umzug und planst, was wohin kommt."),
    ], ("Wohnungsbesichtigung", "Anja zeigt dir ihre neue Wohnung, du stellst Fragen und vergleichst mit deiner.")),
    ("Dein Tag im Detail", "Verben im Präsens, rückbezügliche Verben", [
        ("Morgenroutine", "Du beschreibst deinen Morgen vom Aufstehen bis zum Losgehen."),
        ("Unterwegs zur Arbeit", "Du erzählst, wie du zur Arbeit kommst und was unterwegs passiert."),
        ("Feierabend", "Du sagst, wie du abschaltest und was du abends machst."),
        ("Wochenende", "Du erzählst, wie ein gutes Wochenende für dich aussieht."),
    ], ("Ein typischer Tag", "Ihr vergleicht eure Tage Stunde für Stunde und findet heraus, wer mehr schläft.")),
    ("Menschen beschreiben", "Adjektive passen sich an", [
        ("Aussehen", "Du beschreibst, wie jemand aussieht."),
        ("Charakter", "Du sagst, wie jemand ist: ruhig, offen, stur, herzlich."),
        ("Kleidung und Stil", "Du sprichst über Kleidung und was jemandem steht."),
        ("Größer, älter, ruhiger", "Du vergleichst zwei Menschen miteinander."),
    ], ("Wen du bewunderst", "Du erzählst Anja von einem Menschen, der dich geprägt hat, und beschreibst ihn genau.")),
    ("Haben und Fehlen", "Genitiv: kein, viel, von", [
        ("Was fehlt", "Du sagst, was es nicht gibt und was du nicht hast."),
        ("Viel, wenig, ein paar", "Du nennst Mengen im Alltag."),
        ("Von wem, von wo", "Du sagst, von wem etwas ist und woher jemand kommt."),
        ("Datum und Geburtstage", "Du nennst ein Datum und fragst nach Geburtstagen."),
    ], ("Eine Feier planen", "Ihr plant einen Geburtstag: wer kommt, was fehlt noch, wer bringt was mit.")),
    ("Wohin und Wo", "Richtung und Ort, gehen und fahren", [
        ("Wohin gehst du?", "Du unterscheidest, wohin du gehst und wo du bist."),
        ("Zu Fuß oder fahren", "Du wählst das richtige Verb für gehen und fahren."),
        ("Verkehrsmittel und Tickets", "Du kaufst Fahrkarten und fragst nach Verbindungen."),
        ("Verspätung und Planänderung", "Du sagst Bescheid, wenn etwas dazwischenkommt."),
    ], ("Einen Ausflug organisieren", "Ihr plant einen Tagesausflug: Ziel, Abfahrt, Treffpunkt und Plan B.")),
    ("Geben und Helfen", "Dativ: wem?", [
        ("Wem gibst du was?", "Du sagst, wem du etwas gibst, sagst oder zeigst."),
        ("Geschenke und Feste", "Du überlegst laut, was du wem schenkst."),
        ("Mir ist kalt, mir ist langweilig", "Du drückst Zustände mit dem Dativ aus."),
        ("Was andere brauchen", "Du fragst, was jemand braucht, und bietest Hilfe an."),
    ], ("Ein Geschenk für Anjas Mutter", "Ihr sucht gemeinsam ein Geschenk aus und überlegt, was ihr gefallen könnte.")),
    ("Mit wem und womit", "Instrumental", [
        ("Mit Freunden", "Du sagst, mit wem du etwas machst."),
        ("Beruf und Werden", "Du sagst, als was du arbeitest und was du werden willst."),
        ("Womit?", "Du sagst, womit du etwas machst oder fährst."),
        ("Interessen", "Du erzählst, wofür du dich interessierst und womit du dich beschäftigst."),
    ], ("Was du werden wolltest", "Ihr redet über Kinderträume, heutige Berufe und das, was euch wirklich interessiert.")),
    ("Zeit im Griff", "Uhrzeit, Dauer, Häufigkeit", [
        ("Uhrzeit genau", "Du nennst jede Uhrzeit, auch halb und Viertel."),
        ("Wie lange, seit wann", "Du sagst, wie lange etwas dauert und seit wann du etwas tust."),
        ("Wie oft", "Du sagst, wie häufig du etwas machst."),
        ("Termine verschieben", "Du vereinbarst einen Termin und verschiebst ihn höflich."),
    ], ("Den Wochenplan abstimmen", "Zwei volle Kalender: Ihr findet trotzdem drei gemeinsame Termine.")),
    ("Erledigt oder dabei?", "Verbalaspekt: die Grundidee", [
        ("Fertig oder noch nicht", "Du unterscheidest »ich habe gemacht« von »ich habe fertig gemacht«."),
        ("Einmal oder regelmäßig", "Du wählst die Form für einmalige und für wiederholte Handlungen."),
        ("Anfangen, aufhören, schaffen", "Du sagst, was du begonnen, beendet oder geschafft hast."),
        ("Mit beiden Formen erzählen", "Du erzählst eine kurze Begebenheit mit Hintergrund und Ergebnis."),
    ], ("Was du diese Woche geschafft hast", "Ihr zieht Wochenbilanz: was fertig ist, woran ihr noch sitzt, was liegen blieb.")),
    ("Können, dürfen, müssen", "Modalwörter", [
        ("Können und Beherrschen", "Du unterscheidest »ich kann gerade« von »ich kann überhaupt«."),
        ("Erlaubt und verboten", "Du fragst, ob etwas erlaubt ist, und verstehst Verbote."),
        ("Müssen und Sollen", "Du sagst, was du tun musst und was nötig sein wird."),
        ("Höflich ablehnen", "Du bittest um Erlaubnis und sagst freundlich Nein."),
    ], ("Regeln aushandeln", "Ihr teilt euch eine Ferienwohnung und klärt, wer was darf und wer was muss.")),
    ("Dienstleistungen", "Praktische Situationen", [
        ("Beim Arzt", "Du beschreibst Beschwerden und verstehst Anweisungen."),
        ("Bank, Post, Behörde", "Du erledigst einen einfachen Behördengang."),
        ("Friseur und Reparatur", "Du erklärst, was gemacht werden soll."),
        ("Reklamieren", "Du beschwerst dich sachlich und forderst eine Lösung."),
    ], ("Wenn etwas nicht klappt", "Das Hotelzimmer ist falsch gebucht. Du klärst es ruhig und bekommst, was du brauchst.")),
    ("Meilenstein A2", "Alles aus Phase 2 im Zusammenhang", [
        ("Mit Fremden ins Gespräch kommen", "Du beginnst ein Gespräch und hältst es am Laufen."),
        ("Neuigkeiten des Tages", "Du erzählst, was heute passiert ist, und fragst nach."),
        ("Missverständnisse reparieren", "Du merkst, wenn etwas falsch ankam, und stellst es richtig."),
        ("Bilanz: mein Alltag auf Russisch", "Du erzählst zehn Minuten frei von deinem Alltag."),
    ], ("Großes Alltagsgespräch", "Der Abschluss der zweiten Phase: ein langes Gespräch quer durch deinen Alltag.")),

    # ── Phase 3 · Erzählen und Verstehen (A2+) ──────────────────────────────
    ("Geschichten bauen", "Reihenfolge und Zusammenhang", [
        ("Zuerst, dann, schließlich", "Du erzählst Ereignisse in klarer Reihenfolge."),
        ("Hintergrund und Ereignis", "Du trennst, was gerade lief, von dem, was passierte."),
        ("Plötzlich und deshalb", "Du verbindest Ereignisse mit Überraschung und Folge."),
        ("Eine Anekdote", "Du erzählst eine kurze Geschichte mit Pointe."),
    ], ("Deine peinlichste Geschichte", "Ihr erzählt euch reihum Geschichten, über die ihr heute lachen könnt.")),
    ("Bewegung I", "Gehen und fahren: gerade jetzt oder regelmäßig", [
        ("Unterwegs oder regelmäßig", "Du unterscheidest »ich gehe gerade« von »ich gehe oft«."),
        ("Ich war dort", "Du sagst, wo du warst, mit den Verben der Bewegung."),
        ("Fliegen, laufen, schwimmen", "Du verwendest weitere Bewegungsverben richtig."),
        ("Einen Weg erzählen", "Du beschreibst, wie du irgendwohin gekommen bist."),
    ], ("Dein Weg durch die Stadt", "Du erzählst Anja deinen gestrigen Tag als Weg von Ort zu Ort.")),
    ("Bewegung II", "Vorsilben: an-, weg-, hinein-, hinaus-", [
        ("Ankommen und Weggehen", "Du sagst, wann du kommst und wann du gehst."),
        ("Hinein und hinaus", "Du beschreibst, wer wo hinein- und hinausgeht."),
        ("Vorbei, hinüber, bis", "Du beschreibst Wege mit drei weiteren Vorsilben."),
        ("Reisebericht", "Du erzählst eine Reise von der Abfahrt bis zur Rückkehr."),
    ], ("Die chaotischste Reise", "Alles ging schief: verpasster Zug, falsches Gleis, falsche Stadt. Du erzählst es.")),
    ("Zukunft genau", "Aspekt in der Zukunft, reale Bedingungen", [
        ("Werde tun oder werde erledigen", "Du wählst die richtige Zukunftsform."),
        ("Versprechen und Vorhaben", "Du versprichst etwas und kündigst an, was du vorhast."),
        ("Vermutungen", "Du sagst, was wahrscheinlich passieren wird."),
        ("Wenn, dann", "Du knüpfst Pläne an Bedingungen."),
    ], ("Pläne fürs nächste Jahr", "Ihr malt euch das kommende Jahr aus und überlegt, was davon realistisch ist.")),
    ("Vergleichen und Steigern", "Komparativ und Superlativ", [
        ("Besser, schlechter, mehr", "Du vergleichst mit den wichtigsten Steigerungsformen."),
        ("Als", "Du baust vollständige Vergleichssätze."),
        ("Der beste, am meisten", "Du sagst, was das Beste und das Schlimmste ist."),
        ("Vor- und Nachteile", "Du wägst zwei Möglichkeiten gegeneinander ab."),
    ], ("Stadt oder Land?", "Wo lebt es sich besser? Ihr sammelt Gründe und kommt zu keinem Ergebnis.")),
    ("Der Mensch, der …", "Relativsätze", [
        ("Der Mensch, der", "Du beschreibst Menschen und Dinge mit einem Nebensatz."),
        ("In allen Fällen", "Du verwendest das Relativwort auch mit Vorwörtern."),
        ("Dinge genauer beschreiben", "Du erklärst, welches Ding du meinst."),
        ("Wenn ein Wort fehlt", "Du umschreibst, was du nicht benennen kannst."),
    ], ("Menschen aus deinem Leben", "Du stellst Anja die wichtigsten Menschen deines Lebens vor, ohne Fotos.")),
    ("Er hat gesagt, dass …", "Indirekte Rede", [
        ("Er sagte, dass", "Du gibst wieder, was jemand gesagt hat."),
        ("Sie fragte, ob", "Du gibst Fragen anderer weiter."),
        ("Er bat mich", "Du gibst Bitten und Aufforderungen weiter."),
        ("Neuigkeiten weitererzählen", "Du erzählst, was du gehört hast."),
    ], ("Was man sich so erzählt", "Du berichtest Anja von einem Gespräch, das du gestern mitbekommen hast.")),
    ("Gefühle genau", "Gefühlswortschatz und seine Satzmuster", [
        ("Freude und Stolz", "Du sagst, worüber du dich freust und worauf du stolz bist."),
        ("Ärger und Enttäuschung", "Du sagst ruhig, was dich ärgert oder enttäuscht."),
        ("Angst und Sorge", "Du sprichst über Sorgen, deine und die anderer."),
        ("Mitgefühl zeigen", "Du tröstest und zeigst, dass du verstehst."),
    ], ("Ein schwerer Tag", "Anja hatte einen furchtbaren Tag. Du hörst zu, fragst nach und findest die richtigen Worte.")),
    ("Arbeit konkret", "Berufsalltag", [
        ("Aufgaben und Zuständigkeiten", "Du erklärst, wer bei euch wofür zuständig ist."),
        ("Besprechungen", "Du triffst Absprachen und hältst Ergebnisse fest."),
        ("Probleme melden und lösen", "Du beschreibst ein Problem und schlägst eine Lösung vor."),
        ("Rückmeldung geben", "Du lobst, kritisierst sachlich und nimmst Kritik an."),
    ], ("Projektbesprechung", "Du leitest eine kurze Besprechung: Stand, Probleme, nächste Schritte.")),
    ("Essen als Kultur", "Kochen und Einladen", [
        ("Rezepte", "Du erklärst, wie man ein Gericht zubereitet."),
        ("Russische Küche", "Du kennst die wichtigsten Gerichte und bestellst sie sicher."),
        ("Tischgespräche und Trinksprüche", "Du hältst einen kurzen Trinkspruch."),
        ("Gastgeber sein", "Du lädst ein, bietest an und sorgst für deine Gäste."),
    ], ("Abendessen bei Anjas Familie", "Du bist zum ersten Mal bei ihren Eltern eingeladen und machst alles richtig.")),
    ("Medien und Unterhaltung", "Empfehlen und bewerten", [
        ("Filme und Serien", "Du erzählst, worum es geht, ohne das Ende zu verraten."),
        ("Musik", "Du sprichst über Musik, die dich bewegt."),
        ("Bücher", "Du stellst ein Buch vor, das dich beeinflusst hat."),
        ("Empfehlen und Abraten", "Du empfiehlst etwas und begründest es."),
    ], ("Was ich unbedingt sehen sollte", "Ihr stellt euch gegenseitig eine Liste zusammen und streitet über Geschmack.")),
    ("Zahlen im Leben", "Zahlen in allen Formen", [
        ("Große Zahlen und Preise", "Du nennst und verstehst große Beträge."),
        ("Prozente und Mengen", "Du sprichst über Anteile und Mengen."),
        ("Jahreszahlen und Alter", "Du nennst Jahre, Jahrzehnte und genaue Altersangaben."),
        ("Handeln", "Du verhandelst einen Preis."),
    ], ("Auf dem Markt", "Du willst einen Teppich kaufen. Der Händler ist gut, du bist besser.")),
    ("Niemand und irgendwer", "Verneinung und Unbestimmtes", [
        ("Niemand, nichts, nie", "Du verneinst vollständig, mit doppelter Verneinung."),
        ("Irgendwer, irgendwas", "Du unterscheidest die beiden Arten von »irgend«."),
        ("Weder noch", "Du schließt zwei Dinge gleichzeitig aus."),
        ("Zweifel und Unsicherheit", "Du sagst, dass du dir nicht sicher bist."),
    ], ("Wer war's?", "Der Kuchen ist weg. Ihr rekonstruiert den Abend und verdächtigt jeden.")),
    ("Meilenstein: frei erzählen", "Alles aus Phase 3 im Zusammenhang", [
        ("Dein Lebenslauf", "Du erzählst deinen Werdegang in fünf Minuten."),
        ("Ein Erlebnis ausführlich", "Du erzählst ein Erlebnis mit allen Einzelheiten."),
        ("Zuhören und Nachhaken", "Du stellst Fragen, die ein Gespräch vertiefen."),
        ("Bilanz: ich erzähle frei", "Du erzählst zehn Minuten ohne Vorlage."),
    ], ("Deine Lebensgeschichte", "Der Abschluss der dritten Phase: Du erzählst dein Leben, Anja fragt nach.")),

    # ── Phase 4 · Meinung und Tiefe (B1) ────────────────────────────────────
    ("Meinung begründen", "Standpunkt, Begründung, Einschränkung", [
        ("Ich finde, ich halte für", "Du sagst deine Meinung in mehreren Abstufungen."),
        ("Begründen und Beispiele", "Du stützt deine Meinung mit Gründen und Beispielen."),
        ("Obwohl, dafür, trotzdem", "Du schränkst ein und wägst ab."),
        ("Mit Respekt widersprechen", "Du widersprichst, ohne das Gespräch zu beschädigen."),
    ], ("Eine Streitfrage des Alltags", "Homeoffice oder Büro? Ihr seid verschiedener Meinung und bleibt freundlich.")),
    ("Was wäre, wenn", "Konjunktiv und irreale Bedingung", [
        ("Wenn ich hätte", "Du sprichst über Unwirkliches."),
        ("Wünsche und Bedauern", "Du sagst, was du dir wünschst und was du bereust."),
        ("An deiner Stelle", "Du gibst einen Rat, ohne zu belehren."),
        ("Höfliche Vorschläge", "Du schlägst etwas vorsichtig vor."),
    ], ("Wenn du eine Million hättest", "Ihr spielt durch, was ihr mit viel Geld und viel Zeit anfangen würdet.")),
    ("Ziel und Zweck", "Sätze mit »damit« und »um zu«", [
        ("Wozu?", "Du sagst, wozu du etwas tust."),
        ("Wollen, dass andere", "Du sagst, was du dir von anderen wünschst."),
        ("Indirekt bitten", "Du bittest über einen Nebensatz."),
        ("Motivation erklären", "Du erklärst, was dich antreibt."),
    ], ("Warum du Russisch lernst", "Anja fragt dich ganz direkt nach deinem Warum. Du antwortest ehrlich und ausführlich.")),
    ("Psychologie I: Persönlichkeit", "Fachnaher Wortschatz, Abstraktes", [
        ("Temperament und Typen", "Du beschreibst, wie Menschen verschieden ticken."),
        ("Stärken und Schwächen", "Du sprichst offen über deine eigenen."),
        ("Gewohnheiten ändern", "Du erklärst, wie Veränderung gelingt."),
        ("Motivation und Disziplin", "Du unterscheidest beides und sagst, was dir hilft."),
    ], ("Was dich antreibt", "Ihr analysiert euch gegenseitig, liebevoll und ziemlich treffend.")),
    ("Psychologie II: Beziehungen", "Über Beziehungen sprechen", [
        ("Bindung und Vertrauen", "Du sagst, was Vertrauen für dich bedeutet."),
        ("Grenzen und Bedürfnisse", "Du sagst klar, was du brauchst."),
        ("Miteinander reden", "Du beschreibst, was gute Kommunikation ausmacht."),
        ("Familie und Prägung", "Du erzählst, was du von zu Hause mitbekommen hast."),
    ], ("Was eine gute Beziehung ausmacht", "Ein ruhiges, langes Gespräch darüber, was ihr von einer Beziehung erwartet.")),
    ("Körper und Gesundheit vertieft", "Genau beschreiben und begründen", [
        ("Training und Erholung", "Du erklärst deinen Trainingsplan."),
        ("Ernährung im Detail", "Du begründest, warum du isst, wie du isst."),
        ("Schlaf und Stress", "Du sprichst über Belastung und Ausgleich."),
        ("Beim Arzt ausführlich", "Du schilderst einen Verlauf und verstehst die Diagnose."),
    ], ("Dein Gesundheitsplan", "Anja will fitter werden und bittet dich um einen Plan. Du erklärst jeden Schritt.")),
    ("Business I: Gründen und Führen", "Geschäftssprache", [
        ("Die Geschäftsidee", "Du stellst eine Idee in zwei Minuten vor."),
        ("Kunden und Markt", "Du beschreibst Zielgruppe und Wettbewerb."),
        ("Team und Führung", "Du sagst, wie du führst und was du erwartest."),
        ("Zahlen und Ziele", "Du sprichst über Umsatz, Kosten und Ziele."),
    ], ("Der Pitch", "Du stellst dein Vorhaben einem russischen Investor vor. Anja spielt den Investor.")),
    ("Business II: Verhandeln", "Angebot, Einwand, Abschluss", [
        ("Angebot und Bedingungen", "Du machst ein Angebot und nennst Bedingungen."),
        ("Einwände behandeln", "Du gehst auf Bedenken ein."),
        ("Kompromiss und Abschluss", "Du findest eine Lösung und hältst sie fest."),
        ("Geschäftliche Nachrichten", "Du schreibst kurze, klare Nachrichten an Geschäftspartner."),
    ], ("Die Verhandlung", "Preis, Frist, Umfang: Ihr verhandelt hart und geht zufrieden auseinander.")),
    ("Es wird gemacht", "Passiv und unpersönliche Sätze", [
        ("Es wird gemacht", "Du sagst, was geschieht, ohne den Handelnden zu nennen."),
        ("Ist erledigt", "Du verwendest Kurzformen wie »gemacht«, »geschlossen«, »verboten«."),
        ("Man sagt, es gilt als", "Du gibst wieder, was allgemein gilt."),
        ("Anleitungen und Regeln", "Du verstehst Hinweise, Schilder und Anleitungen."),
    ], ("Wie das bei euch läuft", "Du lässt dir erklären, wie in Russland ein Umzug, eine Hochzeit, ein Behördengang abläuft.")),
    ("Gesellschaft und Alltag in Russland", "Landeskunde im Gespräch", [
        ("Stadt und Land", "Du sprichst über das Leben in Moskau und in der Provinz."),
        ("Feste und Traditionen", "Du kennst die wichtigsten Feste und was man dann sagt."),
        ("Umgangsformen", "Du weißt, was als höflich und was als kalt gilt."),
        ("Deutschland und Russland", "Du vergleichst beide Länder, ohne zu urteilen."),
    ], ("Kulturschock", "Ihr erzählt euch, was euch im Land des anderen am meisten überrascht hat.")),
    ("Geld und Leben", "Über Geld sprechen", [
        ("Miete, Kosten, Sparen", "Du sprichst über Lebenshaltungskosten."),
        ("Arbeit und Gehalt", "Du sprichst über Verdienst, ohne unhöflich zu sein."),
        ("Anlegen und Risiko", "Du erklärst, wie du mit Geld umgehst."),
        ("Was dir Geld wert ist", "Du sagst, wofür du gern Geld ausgibst und wofür nicht."),
    ], ("Lebensstandard", "Was braucht man wirklich? Ein Gespräch über Geld, Zeit und Freiheit.")),
    ("Technik und Zukunft", "Über Entwicklungen sprechen", [
        ("Telefon und Apps", "Du erklärst, wie etwas funktioniert und was nicht geht."),
        ("Künstliche Intelligenz", "Du sprichst über Automatisierung und ihre Folgen."),
        ("Soziale Medien", "Du sagst, wie du sie nutzt und was dich stört."),
        ("Chancen und Gefahren", "Du wägst Nutzen und Risiken ab."),
    ], ("Die Welt in zehn Jahren", "Ihr entwerft zwei Zukunftsbilder, ein gutes und ein schlechtes.")),
    ("Aspekt für Fortgeschrittene", "Aspekt in Befehl, Verneinung, Grundform", [
        ("Befehl und Aspekt", "Du bittest richtig: einmal, dauerhaft, höflich."),
        ("Verneinung und Aspekt", "Du unterscheidest »tu es nicht« von »du musst es nicht tun«."),
        ("Grundform und Aspekt", "Du wählst nach »muss«, »will«, »anfangen« die richtige Form."),
        ("Vorsilben ändern die Bedeutung", "Du erkennst, was Vorsilben aus einem Verb machen."),
    ], ("Anweisungen und Erledigungen", "Ihr bereitet eine Reise vor und verteilt Aufgaben: wer macht was bis wann.")),
    ("Gefühle in Worte fassen II", "Schwierige Gefühle benennen", [
        ("Einsamkeit und Nähe", "Du sprichst darüber, wann du dich allein fühlst."),
        ("Scham und Schuld", "Du sprichst über Fehler, die dich beschäftigen."),
        ("Dankbarkeit und Vergebung", "Du bedankst dich von Herzen und vergibst."),
        ("Krisen und Neuanfang", "Du erzählst von einer schweren Zeit und was danach kam."),
    ], ("Was dich geprägt hat", "Ein Gespräch über die Zeiten, die euch zu denen gemacht haben, die ihr seid.")),
    ("Sinn und Werte vertieft", "Abstrakt und trotzdem klar sprechen", [
        ("Freiheit und Verantwortung", "Du sagst, wie beides für dich zusammenhängt."),
        ("Erfolg und Glück", "Du unterscheidest, was dich erfolgreich und was dich glücklich macht."),
        ("Glaube und Zweifel", "Du sprichst respektvoll über Überzeugungen."),
        ("Was bleibt", "Du sagst, was du hinterlassen möchtest."),
    ], ("Ein philosophischer Abend", "Kein Thema, kein Ziel: Ihr denkt gemeinsam laut nach.")),
    ("Meilenstein B1", "Alles aus Phase 4 im Zusammenhang", [
        ("Diskutieren ohne Vorlage", "Du hältst zehn Minuten Diskussion durch."),
        ("Einen Standpunkt halten", "Du bleibst bei deiner Meinung, auch unter Druck."),
        ("Zusammenfassen und vermitteln", "Du fasst zwei Standpunkte zusammen und findest das Gemeinsame."),
        ("Bilanz: ich diskutiere frei", "Du diskutierst ein Thema deiner Wahl."),
    ], ("Das große Streitgespräch", "Der Abschluss der vierten Phase: ein Thema, zwei Meinungen, eine Stunde.")),

    # ── Phase 5 · Fließend und nuanciert (B1+) ──────────────────────────────
    ("Die kleinen Wörter I", "Partikeln, die Sätze lebendig machen", [
        ("Doch und ja", "Du verwendest die häufigste russische Partikel in ihren Bedeutungen."),
        ("Schließlich, ja doch", "Du erinnerst an etwas, das der andere weiß."),
        ("Da, hier, sieh mal", "Du zeigst und betonst mit zwei kleinen Wörtern."),
        ("Na, also, und", "Du beginnst Sätze so, wie man es wirklich tut."),
    ], ("Klingen wie von dort", "Dasselbe Gespräch zweimal: erst korrekt, dann lebendig.")),
    ("Die kleinen Wörter II", "Füllwörter und Betonung", [
        ("Zeit gewinnen", "Du überbrückst Denkpausen, ohne zu verstummen."),
        ("Abschwächen", "Du sagst etwas vorsichtiger."),
        ("Genau, sogar, nur", "Du hebst hervor, was dir wichtig ist."),
        ("Etwa? Wirklich?", "Du drückst Erstaunen und Zweifel mit Fragepartikeln aus."),
    ], ("Spontan reagieren", "Anja überrascht dich mit Neuigkeiten. Du reagierst sofort und passend.")),
    ("Umgangssprache", "Wie man unter Freunden redet", [
        ("Alltagssprache", "Du verstehst und verwendest lockere Alltagswörter."),
        ("Internet und Jugendsprache", "Du verstehst, was in Chats steht."),
        ("Verkleinerungen und Kosenamen", "Du verstehst, warum alles ein »-chen« bekommt."),
        ("Was wohin passt", "Du weißt, was du wo besser nicht sagst."),
    ], ("Ein Abend mit Freunden", "Du bist mit Anjas Freunden unterwegs. Alle reden schnell, du hältst mit.")),
    ("Förmlich und offiziell", "Die Sie-Form sicher beherrschen", [
        ("Vorname und Vatersname", "Du sprichst Menschen förmlich korrekt an."),
        ("Offizielle Schreiben", "Du verstehst Bescheide und schreibst eine kurze Anfrage."),
        ("Telefonate mit Ämtern", "Du klärst ein Anliegen am Telefon."),
        ("Höflich bestehen", "Du bleibst freundlich und lässt dich nicht abwimmeln."),
    ], ("Termin beim Amt", "Ein Formular fehlt, die Zeit läuft. Du bleibst höflich und bekommst deinen Stempel.")),
    ("Redewendungen I", "Bilder aus Körper und Alltag", [
        ("Kopf und Herz", "Du verstehst Wendungen rund um Denken und Fühlen."),
        ("Hand und Fuß", "Du verstehst Wendungen rund ums Handeln."),
        ("Essen und Trinken", "Du verstehst Wendungen aus der Küche."),
        ("Tiere", "Du verstehst, wer hier Bär und wer Hase ist."),
    ], ("Sprichwörter erklären", "Du erklärst deutsche Redewendungen auf Russisch, Anja die russischen.")),
    ("Redewendungen II", "Sprichwörter und Humor", [
        ("Sprichwörter", "Du kennst die zwanzig, die wirklich jeder sagt."),
        ("Ironie", "Du erkennst Ironie und setzt sie ein."),
        ("Witze", "Du verstehst einen Witz und erzählst selbst einen."),
        ("Wortspiele", "Du verstehst, warum etwas doppeldeutig ist."),
    ], ("Die lustigste Geschichte", "Wer zuerst lacht, verliert. Ihr erzählt euch Witze und Missgeschicke.")),
    ("Erzählkunst", "Partizipien verstehen", [
        ("Der lesende, der gelesene", "Du verstehst Partizipien in Texten."),
        ("Während und nachdem", "Du verstehst und bildest Sätze mit Adverbialpartizip."),
        ("Spannend erzählen", "Du baust Spannung auf und setzt Pausen."),
        ("Nacherzählen", "Du gibst den Inhalt eines Films oder Buchs wieder."),
    ], ("Erzähl mir einen Film", "Du erzählst deinen Lieblingsfilm so, dass Anja ihn sehen will.")),
    ("Bewegung III", "Bewegungsverben im übertragenen Sinn", [
        ("Zeit vergeht, es passt, es klappt", "Du verwendest Bewegungsverben für Abstraktes."),
        ("Erreichen, übergehen, vorbeischauen", "Du verstehst drei weitere übertragene Bedeutungen."),
        ("Führen und Tragen", "Du verwendest die Verben für bringen, führen, tragen."),
        ("Feste Wendungen", "Du kennst die häufigsten Wendungen mit Bewegungsverben."),
    ], ("Wie es wirklich läuft", "Ihr sprecht darüber, wie es im Leben gerade vorangeht, in lauter Bewegungsverben.")),
    ("Zahlen, Zeit und Fälle komplett", "Zahlwörter in allen Fällen", [
        ("Das Datum in allen Fällen", "Du sagst »am«, »seit dem«, »bis zum« mit Datum."),
        ("Zeiträume und Fristen", "Du sprichst über Dauer und Termine."),
        ("Zu zweit, zu dritt, beide", "Du verwendest Sammelzahlen."),
        ("Ungefähr", "Du schätzt und rundest."),
    ], ("Ein großes Vorhaben planen", "Ihr plant ein Projekt über sechs Monate mit Terminen, Kosten und Puffern.")),
    ("Russische Kultur I", "Film und Musik", [
        ("Kultfilme und Zitate", "Du erkennst Zitate, die jeder kennt."),
        ("Von Rock bis Pop", "Du sprichst über russische Musik."),
        ("Lieder verstehen", "Du verstehst einen Liedtext Zeile für Zeile."),
        ("Über Kunst sprechen", "Du sagst, was dich an einem Werk berührt."),
    ], ("Dein Lieblingslied", "Du erklärst Anja ein Lied, das dir viel bedeutet, und sie dir eines von ihr.")),
    ("Russische Kultur II", "Literatur, Geschichte, Regionen", [
        ("Klassiker in einfachen Worten", "Du weißt, worum es in den großen Romanen geht."),
        ("Ein Gedicht", "Du lernst ein kurzes Gedicht auswendig und trägst es vor."),
        ("Geschichte in Stichworten", "Du ordnest die wichtigsten Ereignisse ein."),
        ("Orte und Regionen", "Du kennst die großen Landschaften und Städte."),
    ], ("Eine Reise durch Russland", "Ihr plant drei Wochen: von Petersburg bis zum Baikal.")),
    ("Nachrichten verstehen", "Mediensprache", [
        ("Schlagzeilen", "Du verstehst die verdichtete Sprache der Überschriften."),
        ("Bericht und Meinung", "Du erkennst, wo berichtet und wo gewertet wird."),
        ("Wirtschaft und Politik", "Du kennst den Grundwortschatz."),
        ("Über ein Ereignis sprechen", "Du gibst eine Nachricht wieder und ordnest sie ein."),
    ], ("Die Nachricht der Woche", "Jeder bringt eine Nachricht mit und erklärt, warum sie wichtig ist.")),
    ("Überzeugen und Präsentieren", "Vor anderen sprechen", [
        ("Aufbau", "Du gliederst einen Vortrag."),
        ("Zahlen und Grafiken", "Du erklärst eine Entwicklung."),
        ("Die Zuhörer abholen", "Du beginnst so, dass man dir zuhört."),
        ("Fragen beantworten", "Du gehst souverän mit Zwischenfragen um."),
    ], ("Dein Kurzvortrag", "Fünf Minuten über ein Thema, das dir wichtig ist. Danach Fragen.")),
    ("Schwierige Gespräche", "Klar und menschlich bleiben", [
        ("Kritik äußern", "Du sagst etwas Unangenehmes klar und fair."),
        ("Schlechte Nachrichten", "Du überbringst eine schlechte Nachricht."),
        ("Nein sagen", "Du lehnst ab und bleibst dabei."),
        ("Beruhigen", "Du nimmst die Schärfe aus einem Gespräch."),
    ], ("Das Konfliktgespräch", "Ein Freund hat dich enttäuscht. Du sagst es ihm.")),
    ("Nähe und Partnerschaft vertieft", "Über das Gemeinsame sprechen", [
        ("Gemeinsame Zukunft", "Du sprichst über Wünsche, die euch beide betreffen."),
        ("Alltag zu zweit", "Du klärst Alltägliches, ohne dass es Streit gibt."),
        ("Eifersucht und Vertrauen", "Du sprichst über Unsicherheit."),
        ("Zärtlichkeit und Humor", "Du neckst liebevoll."),
    ], ("Worüber Paare reden", "Ein ganz normaler Abend, an dem über alles gesprochen wird.")),
    ("Meilenstein: natürlich klingen", "Alles aus Phase 5 im Zusammenhang", [
        ("Spontan über jedes Thema", "Du sprichst zwei Minuten über ein Thema, das du vorher nicht kennst."),
        ("Umschreiben und Reparieren", "Du rettest jeden Satz, auch wenn ein Wort fehlt."),
        ("Tempo und Rhythmus", "Du sprichst flüssig und setzt Pausen bewusst."),
        ("Bilanz: ich klinge natürlich", "Du führst ein Gespräch, in dem niemand langsamer für dich spricht."),
    ], ("Zwei Stunden nur Russisch", "Der Abschluss der fünften Phase: ein ganzer Abend ohne ein deutsches Wort.")),

    # ── Phase 6 · Souverän (B2) ─────────────────────────────────────────────
    ("Argumentieren", "These, Zugeständnis, Schluss", [
        ("These und Gegenthese", "Du baust eine Argumentation auf."),
        ("Zugestehen und Kontern", "Du gibst einen Punkt zu und setzt nach."),
        ("Abstraktes ausdrücken", "Du verwendest abstrakte Begriffe sicher."),
        ("Genau formulieren", "Du triffst die Nuance, die du meinst."),
    ], ("Die Debatte", "Ein Thema, Los entscheidet die Seite. Du vertrittst sie, auch wenn es nicht deine ist.")),
    ("Feinheiten", "Aspekt, Zeitenfolge, Stil", [
        ("Aspekt in Grenzfällen", "Du verstehst, warum hier diese Form steht und nicht die andere."),
        ("Bedingung und Folge", "Du baust mehrstufige Bedingungssätze."),
        ("Zeitenfolge", "Du erzählst mit Rückblenden und Vorgriffen."),
        ("Knapp oder kunstvoll", "Du wählst den Stil passend zur Situation."),
    ], ("Eine Geschichte, drei Stile", "Du erzählst dieselbe Begebenheit sachlich, lustig und dramatisch.")),
    ("Beruflich bestehen", "Geschäftssprache auf hohem Niveau", [
        ("Vorstellungsgespräch", "Du stellst dich und deinen Werdegang überzeugend vor."),
        ("Eine Besprechung leiten", "Du führst durch die Tagesordnung und kommst zu Entscheidungen."),
        ("Verträge verstehen", "Du verstehst die wichtigsten Klauseln und fragst gezielt nach."),
        ("Kontakte knüpfen", "Du kommst mit Fremden beruflich ins Gespräch."),
    ], ("Das Geschäftsessen", "Erst wird gegessen, dann verhandelt. Du beherrschst beides.")),
    ("Wissen und Ideen", "Fachsprache deiner Themen", [
        ("Psychologie", "Du sprichst fachlich über Verhalten und Denken."),
        ("Gesundheit und Medizin", "Du verstehst Befunde und erklärst Zusammenhänge."),
        ("Technik und Wirtschaft", "Du erklärst, wie ein Geschäftsmodell oder ein System funktioniert."),
        ("Einen Fachtext wiedergeben", "Du fasst einen Artikel zusammen und beurteilst ihn."),
    ], ("Erklär mir dein Fach", "Du erklärst Anja, woran du arbeitest, so dass sie es wirklich versteht.")),
    ("Emotionale Tiefe", "Die großen Gespräche", [
        ("Trost und Beistand", "Du bist für jemanden da, auch sprachlich."),
        ("Über Verlust sprechen", "Du sprichst über Abschied und Trauer."),
        ("Träume und Ängste teilen", "Du zeigst, was dich im Innersten bewegt."),
        ("Große Worte", "Du sagst, was jemand dir bedeutet."),
    ], ("Das wichtigste Gespräch", "Es gibt etwas, das du ihr schon lange sagen wolltest.")),
    ("Leben auf Russisch", "Alltag, Freundschaft, Persönlichkeit", [
        ("Im Ausland leben", "Du regelst Wohnen und Arbeiten in russischer Sprache."),
        ("Freundschaften pflegen", "Du hältst Kontakt, gratulierst, fragst nach."),
        ("Schlagfertigkeit", "Du antwortest schnell und mit Witz."),
        ("Deine Stimme auf Russisch", "Du klingst auf Russisch wie du selbst."),
    ], ("Ein Abend, nur ihr zwei", "Kein Plan, kein Thema. Ihr redet, bis es hell wird.")),
    ("Jahresabschluss", "Rückblick und Ausblick", [
        ("Rückblick auf das Jahr", "Du erzählst, wie dein Jahr mit der Sprache war."),
        ("Was sich verändert hat", "Du sagst, was du heute kannst und früher nicht konntest."),
        ("Ziele für das nächste Jahr", "Du sagst, wohin du mit der Sprache willst."),
        ("Deine Abschlussrede", "Du hältst eine kurze Rede auf Russisch."),
    ], ("Das Jahresgespräch", "Tag 365. Ein ganzes Gespräch über ein ganzes Jahr, frei und auf Russisch.")),
]
