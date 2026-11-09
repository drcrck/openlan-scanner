# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Technicians and tinkerers: system administrators, IT technicians and network enthusiasts who move between office and customer networks. They want precise data (IP, MAC and its source, vendor, open ports, diagnostics) rather than simplified explanations. Home users may use it too, but they are not the design target.

## Product Purpose

OpenLAN Scanner discovers and identifies the devices on the local network the phone/tablet is connected to, and provides the tools to investigate them: port scan with one-tap open (browser, SSH, SMB, RDP, VNC, RTSP), live ping chart, traceroute to the Internet, and a raw diagnostics log. Success means a technician understands an unfamiliar network in a couple of minutes and can act on what they find.

## Positioning

- **Verifiably open source:** full source public on GitHub (drcrck/openlan-scanner), GPL-3.0-or-later. Anyone can check what the app does.
- **Everything local, zero data collected:** no cloud, no account, no analytics; scan results stay on the device.
- **Free, no paid features:** no subscription or premium tier, ever. The Play build carries at most one small AdMob banner, only at the bottom of the device detail page; the `foss` build has none.

These three are the differentiators against Fing and similar apps and must never be contradicted.

## Operating Context

- **On-site visits at customer networks:** arriving on an unknown network and quickly finding the gateway, NAS, printers, servers and remote-management cards.
- **Checking one's own network** (home or office): spotting new or unknown devices.
- **Troubleshooting:** ping, open ports, traceroute towards the Internet, CGNAT detection.

Used on Android phones and tablets (tested on a Galaxy Tab A8), often one-handed and in the field.

## Capabilities and Constraints

- Discovery: ping sweep + TCP probe (subnets up to /22, larger reduced to the phone's /24), NetBIOS NBSTAT, mDNS (unicast + multicast listener), SSDP/UPnP description XML, reverse DNS against the network's DNS servers.
- **No ARP:** Android 10+ blocks the ARP table (targetSdk 36). MACs come only from what devices announce (NetBIOS, mDNS, UPnP) and the source is always shown. Devices that announce nothing have no MAC; this is a platform limit, not a bug.
- Vendor lookup offline from the IEEE OUI registry (via Wireshark `manuf`).
- Device recognition across scans: MAC → UPnP UDN → NetBIOS → mDNS → DNS name → IP (IP-only is flagged as uncertain).
- Must work on any network without configuring the router (no SNMP, agents or API keys).
- Location permission is used only to read SSID/BSSID.
- Two build flavors from one codebase: `foss` (no ads, no Google libraries) and `play` (AdMob + UMP consent).
- Languages: English (default) and Italian; every UI string exists in both.
- Stack: Kotlin, Jetpack Compose (Material 3), Room, coroutines; minSdk 26.

## Brand Commitments

- Name: **OpenLAN Scanner** (package `io.github.drcrck.openlan`, immutable once on Play). Published under the author's own name, Diego Rigoni.
- "Open source" is the main message across the app, the store listing and the README.
- Voice: direct and technically honest; states where data comes from and what Android does not allow.

## Evidence on Hand

- Source code, README, PRIVACY.md and GPL-3.0 LICENSE in the public repository.
- Store texts and graphics (icon 512, feature graphic 1024×500, EN/IT) in `fastlane/metadata/android/`.
- No users, reviews, download counts or testimonials exist yet; do not fabricate them.

## Product Principles

1. Precision over simplification: show the real data (IP, MAC, source, ports) to people who understand it.
2. Honesty about limits: label the origin of every identifier and explain platform restrictions instead of hiding them.
3. Nothing leaves the device: any new feature must work offline/locally and without accounts.
4. Fast to understand an unknown network: the most useful facts (gateway, roles, device type, online state) come first.
5. Monetization never degrades the tool: ads stay confined to the single detail-page banner in the Play build.
