### Changelog

#### Version 2.0.2
* bug fixes especially for Android 4

#### Version 2.0.1
* improved recording quality
* let the user select a public server for account creation
* add possibility to de-/activate accounts in multi account mode
* bug fixes 

#### Version 2.0.0
* highlight irregular unicode code blocks in Jabber IDs
* integrate QR code scanner (requires camera permission)
* removed support for customizable resources
* reworked message search
* added splash screen at startup
* integrate dark theme
* keep OTR, but without further development and support, just for compatibility
* added configurable font size
* added global OMEMO preference
* added scroll to bottom button
* added contact button as android widget
* only mark visible messages as read
* a lot of bug fixes

#### Version 1.22.1
* show extended/TOR connection options in expert settings
* bug fixes

#### Version 1.22.0
* integrated expert option to enable multiple account
* some UI improvements
* improved MAM support
* bug fixes 

#### Version 1.21.3
* small self messages improvement
* small notification improvement
* bug fixes

#### Version 1.21.2
* bug fixes

#### Version 1.21.1
* don't use integrated updater if Messenger is installed from F-Droid
* check if app installs from unknown sources are allowed, if not open settings to allow this
* show hint in chatview if private message is activated
* send delivery receipts after MAM catchup
* reduce number of wake locks
* add possibility to destroy group chats
* show progress dialog while downloading update with internal updater
* implemented message search
* improved self chat
* bug fixes

#### Version 1.21.0
* replaced google maps location service with open street map services via leaflet
* let screen on while playing audio files
* add Turkish translations
* bug fixes

#### Version 1.20.3
* show icon instead of image preview in conversation overview
* fixed loop when trying to decrypt with YubiKey
* Removed NFC support
* Fixed OMEMO device list no being announced
* bug fixes

#### Version 1.20.2
* bug fixes

#### Version 1.20.1
* bug fixes

#### Version 1.20.0
* reworked AppUpdater and show a notification if there is an update available
* some UI and performance improvements
* add french and spanish translations
* text markup *bold*, _italic_,`monospace` and ~strikethrough~
* fixed 'No permission to access …' when opening files shared from the SD card
* always open URLs in new tab
* bring back quick share (default off) and return to previous app after sharing
* send and show read markers in private, non-anonymous groups
* warn if chat is unencrypted and switch to OMEMO if available after pressing OK (warning can be disabled in settings)
* support sending and receiving opus file
* bug fixes


#### Version 1.19.2
* reworked inline player for audio messages
* long press the 'add back' button to show block menu
* foregroundservice can be switched off 
* bug fixes

#### Version 1.19.1
* fixed OTR encrypted file transfer
* bug fixes

#### Version 1.19.0
* Added 'App Shortcuts' to quickly access frequent contacts
* Use DNSSEC to verify hostname instead of domain in certificate
* Setting to enable Heads-up notifications
* Made DNSSEC hostname validation opt-in
* work around for OpensFire's self signed certs
* use VPN's DNS servers first
* fixed connection loop on Android < 4.4
* presence subscription no longer required for OMEMO on compatible servers
* bug fixes


#### Version 1.18.2
* colorize send button only after history is caught up
* improved MAM catchup strategy
* bug fixes

#### Version 1.18.1
* limited GPG encryption for MUC offline members
* show extended connection settings for open version in export settings
* fixed landscape layout problems for tablets
* fixed UI freezes during connection timeout
* fixed notification sound playing twice
* fixed conversations being marked as read
* removed 'copy text' in favor of 'select text' and 'share with'
* bug fixes

#### Version 1.18.0
* enable OTR and OpenPGP again
* fix bug with updater on devices < SDK 21
* Show colored contact names for their presence status (via settings)
* treat URL as file if URL is in oob or contains key
* added support for Android Auto
* fixed HTTP Download over Tor
* work around for nimbuzz.com MUCs
* bug fixes

#### Version 1.17.1
* grey out offline contacts in StartConversation
* change emoji library which supports newer emojis
* change avatar images to circles
* Switch Aztec to QR for faster scans
* make automatic fullscreen media rotation configurable
* open version can use tor networks
* make image compression configurable
* show read/received markers in chatlist
* OMEMO: put auth tag into key (verify auth tag as well)
* offer to block entire domain in message from stanger snackbar
* bug fixes

#### Version 1.17.0
* add date bubbles in chat view
* show last used resource in contact details
* make brightness overwrite in image/video viewer configurable
* make video resolution configurable
* do not notify for messages from strangers by default
* blocking a JID closes the corresponding conversation
* show message sender in conversation overview
* Support for the latest MAM namespace
* Icons for attach menu
* send typing notifications in private, non-anonymous MUCs
* change media directory names
* bug fixes

#### Version 1.16.5
* show read marker in whisper messages
* bug fixes 

#### Version 1.16.4
* show failed file uploads in chatlist
* resend failed file uploads automatically
* preview files in chatlist
* move (un)mute settings to contact-/conference details
* UI performance fixes
* bug fixes

#### Version 1.16.3
* don't use jingle as fallback if file is too big
* bug fixes

#### Version 1.16.2
* show app name and version from shared apk files
* add ability to compress videos with 720p and not only bigger ones

#### Version 1.16.1
* bug fixes

#### Version 1.16.0
* show unread messages in chatlist
* increase image size to 4k UHD
* add support for GIF files
* reworked video compression
* reworked app updater
* bug fixes

#### Version 1.15.4
* improve video compression quality
* support for POSH (RFC7711)
* support for quoting messages (via select text)
* verified messages show shield icon; unverified messages show lock
* bug fixes

#### Version 1.15.3
* new [Blind Trust Before Verification](https://gultsch.de/trust.html) mode
* easily share Barcode and XMPP uri from Account details
* bug fixes

#### Version 1.15.2
* automatically remove old OMEMO devices after 7 days
* bug fixes

#### Version 1.15.1
* introduces preference option to choose if videos should be compressed (always, automatically, never)
* bug fixes

#### Version 1.15.0
* make OMEMO working with other clients
* make OMEMO encryption standard for 1:1 chats as default
* start navigation app directly from show location activity
* show map preview on shared locations
* show contacts name on shared VCARDs
* send text directly via ShareWithActivity
* bug fixes 

#### Version 1.14.5
* error message accessible via context menu for failed messages
* don't include pgp signature in anonymous mucs
* bug fixes

#### Version 1.14.4
* make error notification dismissable
* bug fixes

#### Version 1.14.3
* set different auto-download-sizes for mobile, roaming and WiFi connections
* add ability to report errors and bugs directly from menu
* XEP-0377: Spam Reporting
* fix rare start up crashes
* bug fixes

#### Version 1.14.2
* support ANONYMOUS SASL
* add custom Emojis
* scroll long actionbar titles
* some performance improvements
* some video compression improvements
* bug fixes

#### Version 1.14.1
* fix crash on taking photos directly within the app

#### Version 1.14.0
* compress videos > 5 MB before sending
* improvments for Android N
* quick reply to notifications on Android N
* don't download avatars and files when data saver is on
* bug fixes

#### Version 1.13.9
* add icons for files in chat view
* reworked backup service to automatically backup database encrypted to local storage at 4 am each day
* make human readable log export optional
* bug fixes

#### Version 1.13.8
* bug fixes

#### Version 1.13.7
* improved video thumbnails in chatlist
* bug fixes

#### Version 1.13.6
* share image/video directly from fullscreen view
* show online status in foreground service
* support jingle ft:4
* show contact as DND if one resource is
* bug fixes

#### Version 1.13.5
* add image preview before sending single images directly
* add vibrate notification when app is open
* hide actionbar in fullscreen image/video view
* bug fixes

#### Version 1.13.4
* new PGP decryption logic
* bug fixes

#### Version 1.13.3
* new permission check and request at startup
* bug fixes

#### Version 1.13.2
* refactored lastseen info
* bug fixes

#### Version 1.13.1
* bug fixes

#### Version 1.13.0
* changed applicationId
* play videos directly without touching play button
* add database importer from local storage as backup
* changed files directories and names
* bug fixes

#### Version 1.12.6
* bug fixes
* add database exporter to local storage as backup

#### Version 1.12.5
* bug fixes

#### Version 1.12.4
* new create conference dialog
* show first unread message on top
* show geo uri as links
* circumvent long message DOS
* integrate simple videoplayer and image viewer

#### Version 1.12.3
* show offline members in conferences
* various bug fixes

#### Version 1.12.2
* make omemo default when all resources support it
* show presence of other resources as template
* start typing in StartConversationsActivity to search
* show addresses in locations
* show video previews in chats
* various bug fixes and improvements
* fixed pgp presence signing

#### Version 1.12.1
* expert setting to modify presence
* added simple audio player
* added audio recorder
* added location services
* changed theme color from green to blue
* small bug fixes

#### Version 1.12.0
* added welcome screen for first start
* use IP/Port instead of query DNS to improve connection performance
* UI improvements
* bug fixes

#### Version 1.11.7
* Share xmpp uri from conference details
* add setting to allow quick sharing
* use material design icons for android < lollipop
* make foreground service always activated
* disable account deactivation
* UI improvements
* various bug fixes

#### Version 1.11.6
* added preference to disable notification light
* various bug fixes

#### Version 1.11.5
* check file ownership to not accidentally share private files

#### Version 1.11.4
* fixed a bug where contacts are shown as offline
* improved broken PEP detection
* check maximum file size when using HTTP Upload
* properly calculate caps hash
* some UI improvements

#### Version 1.11.3
* only add image files to media scanner
* allow to delete files
* various bug fixes

#### Version 1.11.2
* added voice recorder to plugins
* bug fixes

#### Version 1.11.1
* fixed some bugs when sharing files with Conversations

#### Version 1.11.0
* OMEMO encrypted conferences
* resend failed filetransfers automatically
* Support for XEP-0357: Push Notifications
* disable support for only one account

#### Version 1.10.1
* support only one account
* various bug fixes

#### Version 1.10.0
* Support for XEP-0308: Last Message Correction

#### Version 1.9.4
* prevent cleared Conversations from reloading history with MAM
* various MAM fixes

#### Version 1.9.3
* expert setting that enables host and port configuration
* expert setting opt-out of bookmark autojoin handling
* offer to rejoin a conference after server sent unavailable
* internal rewrites and optimizations

#### Version 1.9.2
* prevent startup crash on Sailfish OS
* minor bug fixes
* removed contact-/conferece-details button
* touch contact name or conferce name in action bar opens contact-/conference-details

#### Version 1.9.1
* minor bug fixes incl. a workaround for nimbuzz.com

#### Version 1.9.0
* Per conference notification settings
* Let user decide whether to compress pictures
* Support for XEP-0368
* Ask user to exclude Conversations from battery optimizations

#### Version 1.8.4
* prompt to trust own OMEMO devices
* fixed rotation issues in avatar publication
* invite non-contact JIDs to conferences

#### Version 1.8.3
* brought text selection back
* hide settings, manage accounts and updater in all menus except in the main activity
* bug fixes

#### Version 1.8.2
* fixed stuck at 'connecting...' bug
* make message box behave correctly with multiple links
* bug fixes

#### Version 1.8.1
* enabled direct share on Android 6.0
* ask for permissions on Android 6.0
* notify on MAM catchup messages
* bug fixes

#### Version 1.8.0
* TOR/ORBOT support in advanced settings
* show vcard avatars of participants in a conference
* Own contact picture in tile for conferences with only one other occupant
* added button to updater dialog to show full changelog
* added plugin loader to settings
* fixed PGP encrypted file transfer
* bug fixes

#### Versrion 1.7.3
* changed app name from Conversations to Pix-Art Messenger
* changed chat background to light yellow
* added own name for sent locations

#### Version 1.7.2
* let users crop their avatars
* bug fixes

#### Versrion 1.7.1
* performance improvements when opening a conversation
* bug fixes

#### Version 1.7.0
* redownload deleted files from HTTP hosts
* bug fixes
* show lastseen info as subitle in single chats

#### Version 1.6.13
* bugfixes
* fetching MUC history via MAM
* Expert setting to automatically set presence
* show client-to-client encryption in chatview
* added changelog to AppUpdater dialog
* delete old version files in download folder on updating
* use standard namespace for file transfers
* CAPTCHA support
* SASL EXTERNAL (client certifiates)

#### Version 1.6.12
* added blue tick as read indicator
* tab completion for MUC nicks
* history export to SD card
* bug fixes

#### Version 1.6.11
* optimized app updater and increased app update check period to once a day

#### Version 1.6.10
* fixed facebook login
* fixed bug with ejabberd mam
* run app updater automatically

#### Version 1.6.9
* basic keyboard support
* bug fixes
* update checker with in app version updates

#### Version 1.6.8
* reworked 'enter is send' setting
* reworked DNS server discovery on lolipop devices
* various bug fixes

#### Version 1.6.7
* bug fixes

#### Version 1.6.6
* best 1.6 release yet

#### Version 1.6.5
* more OMEMO fixes

#### Version 1.6.4
* setting to enable white chat bubbles
* limit OMEMO key publish attempts to work around broken PEP
* various bug fixes

#### Version 1.6.3
* bug fixes

#### Version 1.6.2
* fixed issues with connection time out when server does not support ping

#### Version 1.6.1
* fixed crashes

#### Version 1.6.0
* new multi-end-to-multi-end encryption method
* show unexpected encryption changes as red chat bubbles
* always notify in private/non-anonymous conferences
* some bugfixes
* hard coded pix-art.de as standard server

#### Version 1.5.2
* added new message bubbles
* added subtitles to chatviews in ActionBar to display typing info in single chats and participant names in conferences
* some bug fixes

#### Version 1.5.1
* fixed rare crashes
* improved otr support
* moved typing info to ActionBar

#### Version 1.5.0
* new file transfer mode to offline contacts and conferences for files smaller than 20 MB: upload files to HTTP host and share them in MUCs. requires new [HttpUploadComponent](https://github.com/siacs/HttpUploadComponent) on server side
* default image format is JPEG
* small layout modifications with bigger avatars
* show contacts name in locations shared in conferences

#### Version 1.4.5
* fixes to message parser to not display some ejabberd muc status messages

#### Version 1.4.4
* added unread count badges on supported devices
* rewrote message parser

#### Version 1.4.0
* send button turns into quick action button to offer faster access to take photo, send location or record audio
* visually separate merged messages
* faster reconnects of failed accounts after network switches 
* r/o vcard avatars for contacts
* various bug fixes

#### Version 1.3.0
* swipe conversations to end them
* quickly enable / disable account via slider
* share multiple images at once
* expert option to distrust system CAs
* mlink compatibility
* bug fixes

#### Version 1.2.0
* Send current location. (requires [plugin](https://play.google.com/store/apps/details?id=eu.siacs.conversations.sharelocation))
* Invite multiple contacts at once
* performance improvements
* bug fixes

#### Version 1.1.0
* Typing notifications (must be turned on in settings)
* Various UI performance improvements
* bug fixes

#### Version 1.0.4
* load avatars asynchronously on start up
* support for XEP-0092: Software Version

#### Version 1.0.3
* load messages asynchronously on start up
* bug fixes

#### Version 1.0.2
* skipped

#### Version 1.0.1
* accept more ciphers

#### Version 1.0
* MUC controls (Affiliaton changes)
* Added download button to notification
* Added check box to hide offline contacts
* Use Material theme and icons on Android L
* Improved security
* bug fixes + code clean up

#### Version 0.10
* Support for Message Archive Management
* Dynamically load message history
* Ability to block contacts
* New UI to verify fingerprints
* Ability to change password on server
* removed stream compression
* quiet hours
* fixed connection issues on ipv6 servers

#### Version 0.9.3
* bug fixes

#### Version 0.9.2
* more bug fixes

#### Version 0.9.1
* bug fixes including some that caused Conversations to crash on start

#### Version 0.9
* arbitrary file transfer
* more options to verify OTR (SMP, QR Codes, NFC)
* ability to create instant conferences
* r/o dynamic tags (presence and roster groups)
* optional foreground service (expert option)
* added SCRAM-SHA1 login method
* bug fixes

#### Version 0.8.4
* bug fixes

#### Version 0.8.3
* increased UI performance
* fixed rotation bugs

#### Version 0.8.2
* Share contacts via QR codes or NFC
* Slightly improved UI
* minor bug fixes

#### Version 0.8.1
* minor bug fixes

#### Version 0.8
* Download HTTP images
* Show avatars in MUC tiles
* Disabled SSLv3
* Performance improvements
* bug fixes

#### Version 0.7.3
* revised tablet ui
* internal rewrites
* bug fixes

#### Version 0.7.2
* show full timestamp in messages
* brought back option to use JID to identify conferences
* optionally request delivery receipts (expert option)
* more languages
* bug fixes

#### Version 0.7.1
* Optionally use send button as status indicator

#### Version 0.7
* Ability to disable notifications for single conversations
* Merge messages in chat bubbles
* Fixes for OpenPGP and OTR (please republish your public key)
* Improved reliability on sending messages
* Join password protected Conferences
* Configurable font size
* Expert options for encryption

#### Version 0.6
* Support for server side avatars
* save images in gallery
* show contact name and picture in non-anonymous conferences
* reworked account creation
* various bug fixes

#### Version 0.5.2
* minor bug fixes

#### Version 0.5.1
* couple of small bug fixes that have been missed in 0.5
* complete translations for Swedish, Dutch, German, Spanish, French, Russian

#### Version 0.5
* UI overhaul
* MUC / Conference bookmarks
* A lot of bug fixes

#### Version 0.4
* OTR file encryption
* keep OTR messages and files on device until both parties or online at the same time
* XEP-0333. Mark whether the other party has read your messages
* Delayed messages are now tagged properly
* Share images from the Gallery
* Infinit history scrolling
* Mark the last used presence in presence selection dialog

#### Version 0.3
* Mostly bug fixes and internal rewrites
* Touch contact picture in conference to highlight
* Long press on received image to share
* made OTR more reliable
* improved issues with occasional message lost
* experimental conference encryption. (see FAQ)

#### Version 0.2.3
* regression fix with receiving encrypted images

#### Version 0.2.2
* Ability to take photos directly
* Improved openPGP offline handling
* Various bug fixes
* Updated Translations

#### Version 0.2.1
* Various bug fixes
* Updated Translations

#### Version 0.2
* Image file transfer
* Better integration with OpenKeychain (PGP encryption)
* Nicer conversation tiles for conferences
* Ability to clear conversation history
* A lot of bug fixes and code clean up

#### Version 0.1.3
* Switched to minidns library to resolve SRV records
* Faster DNS in some cases
* Enabled stream compression
* Added permanent notification when an account fails to connect
* Various bug fixes involving message notifications
* Added support for DIGEST-MD5 auth

#### Version 0.1.2
* Various bug fixes relating to conferences
* Further DNS lookup improvements

#### Version 0.1.1
* Fixed the 'server not found' bug

#### Version 0.1
* Initial release
