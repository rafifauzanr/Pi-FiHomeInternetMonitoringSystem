# Pi-Fi Home Internet Monitoring System 🌐
A Raspberry Pi–based home internet monitoring system using Squid Proxy (SSL Bump) for transparent HTTP/HTTPS traffic, BeautifulSoup + spaCy + Sastrawi for NLP and keyword matching, Flask REST API backend, and Android client (Kotlin + Retrofit) for real-time devices, alerts, and logs.
## Supporting Tools 🛠️
- **Hardware:** Raspberry Pi
- **Networking Tools:** hostapd & dnsmasq
- **Traffic Capture:** Squid Proxy (SSL Bump)  
- **Text Parsing:** BeautifulSoup  
- **NLP:** spaCy (EN), Sastrawi (ID)  
- **Backend:** Flask (Python)
- **Logging:** Google Sheets
- **Mobile App:** Kotlin + Retrofit  
## Configuration Workflow ⚙️
1. Raspberry Pi as Internet Access Point
   - Configure hostapd (SSID name & pass) and dnsmasq (DHCP, NAT)
   - Enable IP Forwarding & NAT via iptables
   - Raspberry Pi acts as a Wi-FI hotspot
2. SSL Bump Configuration via Squid Proxy
   - Generate root CA certificate using `sslcrtd_program`
   - Configure `http_port 3128` and `https_port 3129 intercept ssl-bump`
   - Redirect all traffic (HTTP/HTTPS) to Squid via `iptables` 
   - Install the generated CA certificate on smartphones/PC to monitor
3. Keyword Classification
   - Traffic logs from monitored devices stored in `/var/log/squid/access.log`
   - URL extracted from log file to identify accessed websites
   - Text content from webpage extracted using Python library BeautifulSoup
   - Extracted text pre-processed using spaCy & Sastrawi
   - Cleaned & filtered text then classified into categories (Pornography, Online Gambling, Violence) based on keyword matching from a dictionary
4. Backend Integration
   - Output data streamed through Flask REST API Server
   - A background monitoring thread continuously parses Squid logs in real time
   - Flask server provides endpoints for integration with Android client
   - Output data also exported to Google Sheets for long term storage
5. Android Client
   - Connected to Flask REST API Server via endpoints to display monitoring data in real time
   - Provides 4 main features:
     - **Dashboard** – total alerts, connection time summary  
     - **Alerts** – flagged websites (pornography, gambling, violence)  
     - **Devices** – connected device details  
     - **Logs** – browsing history records  
## Screenshots 📷
