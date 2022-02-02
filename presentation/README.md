LaTeX-Vorlage für Präsentationen
================================

Dies ist die Vorlage für Präsentationen am Lehrstuhl Software Design and
Quality (SDQ) am Institut für Datenorganisation und Programmstrukturen (IPD)
des Karlsruher Instituts für Technologie (KIT).

Autor: Dr.-Ing. Erik Burger (burger@kit.edu)
mit Beiträgen von Christian Hammer, Klaus Krogmann und Maximilian Schambach

Siehe https://sdqweb.ipd.kit.edu/wiki/Dokumentvorlagen

Hinweise, Verbesserungsvorschläge
=================================

Bitte verwenden Sie das Issue-Tracking-System unter https://git.scc.kit.edu/i43/dokumentvorlagen/praesentationen/beamer/-/issues, um auf Probleme mit der Vorlage hinzuweisen oder Erweiterungswünsche zu äußern. Sie können gerne auch eine Änderung per Merge-Request vorschlagen.

Verwendung
==========

Das vorliegende Paket dient als Vorlage für Präsentationen im KIT-Design (https://intranet.kit.edu/gestaltungsrichtlinien.php, Fassung vom 1. August 2020).

Es basiert auf LaTeX Beamer (https://ctan.org/pkg/beamer).

Optionen der Dokumentklasse `sdqbeamer`
-----------------------------------------
Durch die folgenden Optionen kann das Seitenverhältnis der Folien bestimmt werden:

| Seitenverhältnis | Option              |
| ---------------- | ------------------- |
| 16:9             | `16:9`  (Standard)  |
| 16:10            | `16:10`             |
| 4:3              | `4:3`               |

Die Schriftgröße in der Fußzeile ist standardmäßig größer gewählt, als in den Gestaltungsrichtlinien angegeben. Die Vorgabe (Schriftgröße 9 -> 6) kann durch die Option `smallfoot` erzwungen werden.

| Schriftgröße Fußzeile | Option               |
| ----------------------| -------------------- |
| etwas größer (12pt)   | `bigfoot` (Standard) |
| KIT-Vorgabe (9pt)     | `smallfoot`          |

Die Plazierung der Navigationsleiste kann durch folgende Optionen beeinflußt werden:

| Position                 | Option           | Bemerkung                                  |
| ------------------------ | ---------------- | ------------------------------------------ |
| oberhalb der Trennlinie  | `navbarinline`   | Standard                                   |
| unterhalb der Trennlinie | `navbarinfooter` | keine Subsection-Punkte, Größe `smallfoot` |
| Seitenleiste links       | `navbarside`     | keine Subsection-Punkte                    |
| keine Navigationsleiste  | `navbaroff`      |                                            |
| KIT-Vorgabe              | `navbarkit`      | entspricht `navbaroff` und `smallfoot`     |

Als Sprache sind Deutsch und Englisch verfügbar. Durch die Sprachwahl werden automatisch die passenden Logos und Formate (z.B. Datum) gewählt.

| Sprache  |                 |
| -------- |---------------- |
| Deutsch  | `de` (Standard) |
| Englisch | `en`            |

Beispiel: `\documentclass[de,16:9,navbarinline]{sdqbeamer}`

Titelbild
---------

Das Bild auf der Titelfolie kann mit dem Befehl 

`\titleimage{myimage}` (ohne Dateiendung)

gesetzt werden. Um ein eigenes Bild zu verwenden, bitte die Datei (z.B. `myimage.jpg`) ins `logos/`-Verzeichnis legen und den Befehl anpassen. Mitgeliefert wird ein generisches Bild aus der KIT-Bildwelt (https://intranet.kit.edu/gestaltungsrichtlinien.php) in der Datei `logos/banner_2020_kit.jpg`. Falls kein Titelbild eingefügt werden soll, bitte `\titleimage{}` setzen.

Für 16:9-Folien sollte das Verhältnis des Bildes 160:37 betragen, für 4:3-Folien 63:20. Es können auch breitere Bilder verwendet werden, da das Titelbild auf die Höhe des Rahmens skaliert und zentriert wird.

Logo und Name Abteilung/KIT-Fakultät/Institut
---------------------------------------------

Das Logo rechts oben auf der Titelfolie kann mit dem folgenden Befehl gesetzt werden:

`\grouplogo{mylogo}` (ohne Dateiendung)

Um ein eigenes Logo zu verwenden, bitte die Datei (z.B. `mylogo.pdf`) in das Verzeichnis `logos/` legen und den Befehl anpassen. Falls kein Logo eingefügt werden soll, bitte `\grouplogo{}` setzen.

Der Gruppenname kann mit folgendem Befehl gesetzt werden:

`\groupname{Software Design and Quality}`.

Der Gruppenname erscheint in der Fußzeile rechts unten. Lange Namen werden in zwei Zeilen umgebrochen. Falls der Gruppenname leer gelassen wird (`\groupname{}`), wird die volle Breite der Fußzeile für Autornamen und Titel verwendet.

LaTeX allgemein
---------------
Siehe https://sdqweb.ipd.kit.edu/wiki/LaTeX

Dateistruktur
============
`presentation.tex`
------------------
Hauptdatei des LaTeX-Dokuments.

`presentation.bib`
-------------
Beispieldatei für BibTeX-Referenzen
https://sdqweb.ipd.kit.edu/wiki/BibTeX-Literaturlisten

`sdqbeamer.cls`
-----------------
Dokumentklasse für Präsentationen im KIT-Design.

`logos/`
--------
In diesem Verzeichnis befinden das KIT-Logo als PDF sowie das Hintergrundbild der Titelfolie als JPG.

`CHANGELOG.md`
--------------
Dokumentation der Änderungen in den jeweiligen Versionen.

`README.md`
-----------
Dieser Text.
