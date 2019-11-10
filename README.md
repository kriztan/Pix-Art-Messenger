![logo](/art/icon.png) 
# Pix-Art Messenger [![CircleCI](https://circleci.com/gh/kriztan/Pix-Art-Messenger/tree/master.svg?style=shield)](https://circleci.com/gh/kriztan/Pix-Art-Messenger/tree/master)

🇬🇧🇺🇸… <a href='README-en.md' style="font-size:150%">English Readme version available here</a>

Pix-Art Messenger ist eine Kopie der offiziellen Android-App [Conversations](https://github.com/siacs/Conversations) mit einigen Änderungen, insbesondere zur Verbesserung der Benutzerfreundlichkeit, um den Umstieg von oftmals vorinstallierten Messengern zu erleichtern. Die folgenden Bilder geben erste Eindrücke der App:

<img src="metadata/en-US/phoneScreenshots/00.png" width="200" /> <img src="metadata/en-US/phoneScreenshots/01.png" width="200" /> <img src="metadata/en-US/phoneScreenshots/02.png" width="200" /> <img src="metadata/en-US/phoneScreenshots/03.png" width="200" /> <img src="metadata/en-US/phoneScreenshots/04.png" width="200" /> <img src="metadata/en-US/phoneScreenshots/05.png" width="200" /> <img src="metadata/en-US/phoneScreenshots/06.png" width="200" />

(Bilder wurden über http://freestockgallery.de bezogen)

Download ist hier möglich:

<a href='https://play.google.com/store/apps/details?id=de.pixart.messenger'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png' height="100"/></a> <a href="https://f-droid.org/app/de.pixart.messenger"><img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="100"></a>

Alternativ kannst du den Messenger auch direkt hier von GitHub unter [Releases](https://github.com/kriztan/Pix-Art-Messenger/releases/latest) herunterladen.

<a rel="me" href="https://social.tchncs.de/@pixart">Pix-Art Messenger bei Mastodon</a> 

#### Pix-Art-Messenger nightly bzw. beta

Scanne den QR-Code und füge es zu deinen F-Droid Quellen hinzu: 

<img src="https://raw.githubusercontent.com/kriztan/Pix-Art-Messenger-nightly/master/icon.png" width="220">

Google-Nutzer können dem Betaprogramm beitreten:
[Pix-Art Messenger beta](https://play.google.com/apps/testing/de.pixart.messenger)

#### Wie kann ich bei der Übersetzung helfen?
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/pix-art-messenger/localized.svg)](https://crowdin.com/project/pix-art-messenger)

Übersetzungen werden bei [crowdin.com](https://crowdin.com/project/pix-art-messenger) geführt. Dort können Übersetzungen erstellt, geändert und ergänzt werden.

[Hier geht's zur Projektseite bei crowdin.com](https://crowdin.com/project/pix-art-messenger/invite?d=75l6j4k6k6k523f4j4m4e473u663d3m4p4t4q4)

#### Ich habe Probleme, was soll ich tun?
Am einfachsten ist es, wenn du unserer Support-Gruppe beitrittst, dort werden deine Probleme mit Sicherheit schnell gelöst. 

Support-Chat: [support@room.pix-art.de](https://jabber.pix-art.de/j/support@room.pix-art.de?join)     
[![Users in muc](https://inverse.chat/badge.svg?room=support@room.pix-art.de)](https://jabber.pix-art.de/j/support@room.pix-art.de?join)

Development-Chat:  [development@room.pix-art.de](https://jabber.pix-art.de/j/development@room.pix-art.de?join)     
[![Users in muc](https://inverse.chat/badge.svg?room=development@room.pix-art.de)](https://jabber.pix-art.de/j/development@room.pix-art.de?join)

Scanne den QR-Code um in den Support-Chat zu gelangen:

<img src="art/qr-code_suport_small.png" width="220">



Solltest du dort nicht weiter kommen kannst du hier ein Issue erstellen, in welchem du dein Problem genau beschreibst und welche Schritte erforderlich sind, um zu dem Problem zu gelangen. 

#### Wie erstelle ich Debug- bzw. ADB-Logs?

##### Linux:

1. Installation von ADB
    ###### Ubuntu / Linux Mint
    ```
    sudo apt-get update
    sudo apt-get install android-tools-adb
    ```
    ###### openSUSE 42.2 und 42.3
    ```
    sudo zypper ref
    sudo zypper install android-tools
    ```
    ###### openSUSE Tumbleweed
    hier muss für das benötigte Packet folgende Repo eingebunden werden (z.B. bequem über Yast):
    http://download.opensuse.org/repositories/hardware/openSUSE_Tumbleweed/
    
    alternativ kann auch der `1 Click Installer` benutzt werden:    
    https://software.opensuse.org/package/android-tools
    
2. Navigiere mit `cd` in das Verzeichnis deines Vertrauens und fahre mit Schritt [6] unter [Windows] fort. s.u.

##### Windows:

1. Lade dir die SDK-Plattform-Tools für dein Betriebssystem von Google herunter:
    
    https://developer.android.com/studio/releases/platform-tools.html    
2. Falls noch nicht getan, lade dir die ADB Treiber für dein Betriebssystem von Google herunter, für Windows hier:
    
    https://developer.android.com/studio/run/win-usb.html
3. Entpacke die zip (z.B. nach C:\ADB\)
4. Öffne die Kommandozeile (CMD) mit Start > Ausführen: cmd
5. Wechsele in der Kommandozeile in das Verzeichnis C:\ADB wie folgt 
    ```
    c:
    cd ADB
    ``` 
6. Auf deinem Telefon gehst du in die Einstellungen und suchst nach dem Punkt `Entwickleroptionen`. Sollte dieser bei dir nicht vorhanden sein, musst du diese Optionen erst noch freischalten. Dazu wechselst du in den Einstellungen in den Punkt `über das Telefon` und suchst dort nach `Buildnummer` oder Ähnlichem. Diese Zeile musst Du mindestens 7 mal hintereinander antippen, es sollte dann ein Hinweis eingeblendet werden, der dir bestätigt, dass du nun Entwickler bist.
7. In den `Entwickleroptionen` suchst du nach dem Eintrag `USB-Debugging` und aktivierst ihn.
8. Schließe dein Handy mit dem USB-Kabel an deinen PC an. Die erforderlichen Treiber sollten zumindest in Windows automatisch installiert werden.
9. Wenn alles ohne Fehler geklappt hat, kannst du wieder in die Kommandozeile gehen und testen, ob alles funktioniert. Gib dazu in CMD `adb devices -l` ein, es sollte in etwa sowas bei dir stehen:
    ```
    > adb devices -l
    List of devices attached
    * daemon not running. starting it now on port 5037 *
    * daemon started successfully *
    042111560169500303f4   unauthorized
    ```
10. Falls dein Handy als `unauthorized` markiert wird, sollte am Handy eine Meldung `USB-Debugging zulassen?` kommen, diese mit `OK` bestätigen, sodass bei `adb devices` folgendes dort stehen sollte:
    ```
    > adb devices
    List of devices attached 
    042111560169500303f4    device
    ```   
11. Nun kannst du mit der Ausgabe der Debug-Logs beginnen. Dazu gibst du im CMD folgendes ein und die Ausgabe beginnt in die Datei `logcat.txt` im Verzeichnis `C:\ADB`:
    ```
    > adb -d logcat -v time | FINDSTR Pix-Art > logcat.txt
    ``` 
12. Führe nun die Schritte aus, die zum Fehler führen.

13. Zum Schluss schaue dir die `logcat.txt` an, lösche ggf. persönliche Angaben und sende diese Datei zur Problemlösung mit einer Beschreibung des Fehlers und was man tun muss, um diesen Fehler zu erhalten, an mich. Nutz dafür den Menüpunkt [Issues](https://github.com/kriztan/Pix-Art-Messenger/issues)
