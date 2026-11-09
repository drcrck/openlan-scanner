# OpenLAN Scanner — documento di progetto

> Documento di lavoro originale, scritto quando il nome provvisorio era "NetScan". Le decisioni tecniche sono ancora valide.

## 1. Obiettivo

Un'app Android personale che replichi le funzioni utili di Fing:

- elenco dei dispositivi presenti sulla rete Wi‑Fi corrente (IP, MAC, nome, vendor, tipo);
- dettaglio dispositivo con port scan, ping, traceroute, Wake on LAN;
- storico delle scansioni e dispositivi "conosciuti" con nome personalizzato;
- notifica quando compare un dispositivo nuovo o sconosciuto;
- info sulla rete (SSID, BSSID, gateway, DNS, subnet, segnale) e speedtest di base.

Nessun account, nessun servizio cloud di terzi, nessuna telemetria.

## 2. Vincoli Android da conoscere prima di partire

| Vincolo | Conseguenza | Soluzione |
|---|---|---|
| Niente raw socket senza root | Non si può fare ARP scan diretto | Ping sweep + sondaggio TCP, poi lettura indiretta della tabella ARP |
| Tabella ARP inaccessibile (`/proc/net/arp` da Android 10, `ip neigh`/netlink per app con targetSdk ≥ 30) | Il MAC degli host non si legge dal kernel | **Decisione: nessuna lettura ARP.** MAC ricavato solo da NetBIOS, mDNS e SSDP (vedi 3.2.1); gli host senza MAC sono identificati da altri attributi |
| `InetAddress.isReachable()` inaffidabile | Molti host risultano "giù" | Usare il binario `ping` di sistema + tentativi TCP su porte comuni (80, 443, 22, 445, 139, 8080) |
| Multicast filtrato di default | mDNS e SSDP non ricevono risposte | `WifiManager.MulticastLock` + permesso `CHANGE_WIFI_MULTICAST_STATE` |
| SSID/BSSID richiedono la posizione | Senza permesso si ottiene `<unknown ssid>` | `ACCESS_FINE_LOCATION` a runtime, posizione attiva sul device |
| Background limitato (Doze) | Il monitoraggio continuo non è garantito | `WorkManager` periodico (min. 15 min) oppure foreground service con notifica persistente |
| MAC randomizzato del telefono | Il proprio device cambia MAC per rete | Irrilevante per gli altri host; mostrare il proprio device come "questo telefono" |

## 3. Funzionalità e tecnica di implementazione

### 3.1 Discovery host

1. Ricavare IP, netmask e gateway da `ConnectivityManager` / `LinkProperties` (API 21+) o `WifiManager.dhcpInfo` (deprecato ma comodo).
2. Calcolare la subnet (limitare a /24–/22 per evitare scansioni enormi; avvisare l'utente se la rete è più grande).
3. Per ogni IP, in parallelo con coroutine su `Dispatchers.IO` (semaforo ~64–128 concorrenti):
   - `ping -c 1 -W 1 <ip>` tramite `ProcessBuilder`;
   - in alternativa/aggiunta, tentativo `Socket.connect` con timeout 300–500 ms su 2–3 porte comuni.
4. Unire i risultati con quelli dei protocolli di discovery (3.2), che forniscono nomi e, quando possibile, il MAC (3.2.1).

### 3.2 Identificazione dispositivi

- **mDNS / Bonjour**: `NsdManager` di Android oppure libreria `jmdns`; servizi tipici `_http._tcp`, `_printer._tcp`, `_airplay._tcp`, `_googlecast._tcp`, `_homekit._tcp`, `_smb._tcp`.
- **NetBIOS Name Service**: pacchetto UDP NBSTAT (`*` query) alla porta 137 di ogni host; restituisce nome macchina e MAC anche per host Windows "silenziosi".
- **SSDP / UPnP**: `M-SEARCH * HTTP/1.1` in multicast a `239.255.255.250:1900`; le risposte contengono `LOCATION` con l'XML del device (friendlyName, manufacturer, modelName).
- **Reverse DNS**: query PTR verso il DNS del router (molti router restituiscono il nome DHCP).
- **DHCP hostname**: non accessibile direttamente; si ottiene indirettamente da NetBIOS/mDNS/rDNS.
- **Vendor da MAC (OUI)**: applicabile solo agli host di cui si è ricavato il MAC (3.2.1). Fonte: file `manuf` di Wireshark (derivato dal registro IEEE, include anche i blocchi /28 e /36; il sito IEEE blocca i download automatici), convertito in `assets/oui.txt` e caricato in memoria alla prima richiesta. Aggiornamento manuale dal menu impostazioni in Fase 5.
- **Tipo dispositivo (euristica)**: combinazione di vendor, servizi mDNS, porte aperte, user-agent SSDP → icona (router, stampante, TV, smart speaker, PC, telefono, IoT).

#### 3.2.1 Fonti del MAC (senza tabella ARP)

| Fonte | Dove si trova il MAC | Copertura tipica |
|---|---|---|
| NetBIOS NBSTAT (UDP 137) | Campo "Unit ID" (6 byte) in coda alla risposta | PC Windows, alcuni NAS. Samba risponde spesso con `00:00:00:00:00:00` → scartare |
| mDNS `_raop._tcp` / `_airplay._tcp` | Nome istanza `AABBCCDDEEFF@Nome` (RAOP), TXT `deviceid=AA:BB:…` (AirPlay) | Apple TV, Mac, speaker AirPlay |
| mDNS `_workstation._tcp` | Nome istanza `host [aa:bb:cc:dd:ee:ff]` | Linux con Avahi, alcuni NAS (Synology, QNAP) |
| mDNS altri TXT | Chiavi tipo `mac=`, `macaddress=`, `hwaddr=` (non standard) | Stampanti, IoT vari: parsing opportunistico |
| SSDP / XML UPnP | Elementi `<macAddress>`, a volte `<serialNumber>`; ultimi 12 hex dell'UDN `uuid:…-xxxxxxxxxxxx` | Router, smart TV, media server. UDN da validare (non sempre è un MAC) |
| Gateway | BSSID da `WifiManager` (MAC radio dell'AP, spesso uguale o adiacente al MAC LAN) | Router/AP a cui si è collegati, utile almeno per il vendor |

Regole:
- normalizzare il formato (`AA:BB:CC:DD:EE:FF`) e scartare valori nulli, broadcast o multicast (bit meno significativo del primo byte a 1);
- segnare i MAC "locally administered" (secondo bit del primo byte a 1): sono randomizzati, quindi non vanno usati per il lookup OUI e non sono stabili nel tempo;
- salvare l'origine del MAC (`NETBIOS`, `MDNS`, `SSDP`, `BSSID`) e la confidenza (diretta vs dedotta da UDN);
- in caso di conflitto prevale la fonte più affidabile: NetBIOS/TXT esplicito > nome istanza mDNS > UDN SSDP > BSSID.

**Limite atteso**: telefoni Android/iOS, molti dispositivi IoT e host che non rispondono a nessun protocollo resteranno senza MAC (e quindi senza vendor). Per loro si mostrano IP, nome (rDNS/mDNS) e servizi rilevati.

### 3.3 Strumenti per singolo host

- **Port scan**: TCP connect scan con lista porte selezionabile (top 100 / top 1000 / range custom), timeout 300 ms, 100 coroutine in parallelo. Banner grabbing opzionale su HTTP/SSH/FTP.
- **Ping**: continuo con statistiche (min/avg/max/loss), via binario `ping`.
- **Traceroute**: `ping -t <ttl>` incrementale (il binario `traceroute` di solito non c'è).
- **Wake on LAN**: magic packet UDP in broadcast su porta 9 (richiede MAC salvato).
- **DNS lookup**: A/AAAA/PTR verso DNS del router o custom (libreria `dnsjava`).

### 3.4 Storico e monitoraggio

- Room DB: tabelle `Network` (BSSID come chiave), `Device`, `ScanRun`, `ScanResult`.
- `Device` ha un id interno (autoincrement) e **non** usa il MAC come chiave primaria, perché il MAC può mancare. Campi: `networkId`, `mac` (nullable), `macSource`, `hostname`, `mdnsName`, `netbiosName`, `ssdpUdn`, `lastIp`, nome custom, prime/ultime viste, note, "fidato".
- Riconoscimento di un dispositivo già visto, in ordine di priorità: MAC globale → UDN SSDP → nome NetBIOS/mDNS → hostname rDNS → IP (solo come ultima ipotesi, segnalata come "incerta" perché il DHCP può riassegnarlo).
- `WorkManager` con `PeriodicWorkRequest` (vincolo: rete Wi‑Fi non metered) che lancia una scansione leggera e confronta con i device fidati.
- Notifica locale per nuovo dispositivo / dispositivo sconosciuto riapparso. Per i dispositivi riconosciuti solo da IP la notifica va marcata come possibile falso positivo.

### 3.5 Info rete e speedtest

- SSID, BSSID, RSSI, frequenza, link speed, canale: `WifiManager.connectionInfo`.
- Gateway, DNS, DHCP lease: `LinkProperties`.
- IP pubblico e ISP: chiamata HTTP a un servizio configurabile (es. una pagina sul proprio server) per evitare dipendenze da terzi.
- Speedtest semplice: download di un file da URL configurabile con misura della velocità media.

## 4. Stack tecnologico

- **Linguaggio**: Kotlin
- **UI**: Jetpack Compose + Material 3, tema chiaro/scuro
- **Concorrenza**: Kotlin Coroutines + Flow
- **Persistenza**: Room
- **Background**: WorkManager
- **DI**: manuale (`AppContainer` in `NetScanApp.kt`); Hilt solo se l'app cresce
- **Rete**: `java.net` (Socket, DatagramSocket), `NsdManager`, OkHttp per le chiamate HTTP
- **Librerie opzionali**: `jmdns`, `dnsjava`
- **Min SDK**: 26 (Android 8) — compromesso ragionevole tra API disponibili e device supportati

### Permessi (AndroidManifest)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

## 5. Struttura del progetto

```
app/
 ├─ data/
 │   ├─ db/            (Room: entità, DAO, database)
 │   ├─ oui/           (lookup vendor da SQLite negli asset)
 │   └─ repository/    (NetworkRepository, DeviceRepository)
 ├─ scanner/
 │   ├─ SubnetScanner.kt     (ping sweep + TCP probe)
 │   ├─ MacResolver.kt       (unione/validazione MAC da NetBIOS, mDNS, SSDP, BSSID)
 │   ├─ MdnsDiscovery.kt
 │   ├─ NetbiosDiscovery.kt
 │   ├─ SsdpDiscovery.kt
 │   ├─ PortScanner.kt
 │   ├─ PingTool.kt / TracerouteTool.kt / WolTool.kt
 │   └─ DeviceClassifier.kt  (euristiche tipo dispositivo)
 ├─ work/
 │   └─ MonitorWorker.kt
 ├─ ui/
 │   ├─ devices/   (lista + dettaglio)
 │   ├─ network/   (info rete, speedtest)
 │   ├─ tools/     (ping, traceroute, port scan, WOL)
 │   ├─ history/
 │   └─ settings/
 └─ di/
```

## 6. Roadmap

### Fase 1 — MVP (scansione e lista)
- Lettura info rete corrente
- Ping sweep + TCP probe
- NetBIOS NBSTAT e reverse DNS (prima fonte di MAC e nomi)
- Lookup OUI sui MAC ottenuti
- Lista dispositivi con IP/nome/MAC/vendor (MAC e vendor quando disponibili), salvataggio su Room
- Rinomina dispositivo

### Fase 2 — Identificazione
- mDNS e SSDP, con estrazione MAC (3.2.1)
- Classificazione tipo dispositivo con icone
- Dettaglio dispositivo

### Fase 3 — Strumenti
- ✅ Port scan nel dettaglio dispositivo, con azioni sulle porte aperte (browser per HTTP/HTTPS, app esterne per ssh://, rdp://, smb://, vnc://, ftp://, rtsp://; se manca l'app l'indirizzo va negli appunti)
- ✅ Traceroute (default 8.8.8.8) con zone LAN / CGNAT / Internet e primo nodo pubblico
- Ping continuo, WOL, DNS lookup

### Fase 4 — Monitoraggio
- WorkManager periodico
- Dispositivi fidati / sconosciuti
- Notifiche nuovo dispositivo

### Fase 5 — Rifiniture
- Export CSV/JSON
- Aggiornamento database OUI dal menu
- Widget home con conteggio dispositivi
- Speedtest

## 7. Alternativa: architettura client/server

Se in casa c'è un server sempre acceso (NAS, Raspberry, container Docker), la scansione può essere delegata a un servizio in Python:

- **Backend**: FastAPI + `scapy` (ARP scan reale, risultati istantanei e completi) o `python-nmap`; scheduler interno per scansioni periodiche; SQLite per lo storico; endpoint REST `GET /devices`, `GET /scans`, `POST /scan`, `POST /wol/{mac}`, WebSocket per gli eventi "nuovo dispositivo"; notifiche push via ntfy self-hosted.
- **App Android**: client leggero che mostra i dati del server, con fallback alla scansione locale quando il server non è raggiungibile (es. fuori casa).

Vantaggi: affidabilità molto superiore (ARP a livello 2, nessun limite Android), storico completo anche a telefono spento, nessun consumo batteria. Svantaggio: funziona solo sulla rete dove gira il server.

Le due strade non si escludono: il modulo `scanner/` locale resta utile come fallback e per le reti esterne.

## 8. Risorse

- Database OUI IEEE: https://standards-oui.ieee.org/oui/oui.csv
- Specifica NetBIOS NBSTAT: RFC 1002
- SSDP/UPnP Device Architecture: specifica UPnP 2.0
- `NsdManager`: documentazione Android Developers
- Prima di iniziare, cercare su GitHub e F-Droid scanner di rete open source in Kotlin da usare come riferimento per le parti di parsing (NetBIOS, SSDP).
