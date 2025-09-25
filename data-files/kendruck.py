import time
import re
import requests
import spacy
from bs4 import BeautifulSoup
from Sastrawi.Stemmer.StemmerFactory import StemmerFactory
import pandas as pd
import os
import re
from urllib.parse import urlparse
import datetime
from flask import Flask, jsonify, request, send_file
import gspread
from oauth2client.service_account import ServiceAccountCredentials

import threading

app = Flask(__name__)

total_alerts = 0
alert_lock = threading.Lock()

alerts_history = []
alerts_history_lock = threading.Lock()

web_activity_history = []
web_activity_lock = threading.Lock()
connected_devices = {}
device_lock = threading.Lock()
device_timeout = 300

# Setup Google Sheets
scope = ["https://spreadsheets.google.com/feeds", "https://www.googleapis.com/auth/drive"]
creds = ServiceAccountCredentials.from_json_keyfile_name("/home/rafi/pi-fi/credentials.json", scope)
gs_client = gspread.authorize(creds)
SPREADSHEET_NAME = "Internet Monitoring Pi-Fi"
sheets = gs_client.open(SPREADSHEET_NAME).worksheets()
print("[GSHEET] Worksheets tersedia:", [s.title for s in sheets])
sheet_web_logs = gs_client.open(SPREADSHEET_NAME).worksheet("web_activity_logs")
sheet_alerts = gs_client.open(SPREADSHEET_NAME).worksheet("alerts")
sheet_devices = gs_client.open(SPREADSHEET_NAME).worksheet("devices")

# Load keyword-based word lists
data_dir = "/home/rafi/pi-fi/data-files"
pornografi = pd.read_csv(os.path.join(data_dir, "pornografi.csv"))["word"].tolist()
kata_kasar = pd.read_csv(os.path.join(data_dir, "kata_kasar.csv"))["word"].tolist()
judi_online = pd.read_csv(os.path.join(data_dir, "judi_online.csv"))["word"].tolist()
blokir_kominfo = pd.read_csv(os.path.join(data_dir, "blokir_kominfo.csv"), header=None)[0].astype(str).tolist()

nlp = spacy.load("en_core_web_sm")
stemmer = StemmerFactory().create_stemmer()

situs_pornografi_pd = pd.read_csv(os.path.join(data_dir, "situs_pornografi.csv"), header=None)
situs_judi_online_pd = pd.read_csv(os.path.join(data_dir, "situs_judionline.csv"), header=None)
dilarang_dikunjungi_pd = pd.read_csv(os.path.join(data_dir,"situs_diblokir.csv"), header=None)
SITUS_PORNOGRAFI = situs_pornografi_pd[0].astype(str).tolist()
SITUS_JUDI_ONLINE = situs_judi_online_pd[0].astype(str).tolist()
DILARANG_DIKUNJUNGI = dilarang_dikunjungi_pd[0].astype(str).tolist()

DOMAIN_CATEGORIES = {
    'Pornografi': SITUS_PORNOGRAFI,
    'Judi Online': SITUS_JUDI_ONLINE,
    'Dilarang_dikunjungi': DILARANG_DIKUNJUNGI
}

squid_logs = "/var/log/squid/access.log"
unncsry = [
    # Ekstensi file
    r'\.(jpg|jpeg|png|gif|webp|svg|ico|bmp|tiff)(\?.*)?$',
    r'\.(mp3|mp4|webm|ogg|avi|mov|wmv|flv)(\?.*)?$',
    r'\.(woff|woff2|ttf|eot|otf)(\?.*)?$',
    r'\.(css|js|map)(\?.*)?$',
    
    # Path tracking dan ads
    r'/ads?/',
    r'/advert/',
    r'/track',
    r'/analytics',
    r'/pixel',
    r'/beacon',
    r'/api/',
    r'/rest/',
    r'/ajax/',
    r'/static/',
    r'/assets/',
    r'/cdn-cgi/',
    r'/safeframe/',
    r'/cookiesync',
    r'/usersync',
    r'/sync\?',
    
    # Parameter tracking
    r'[?&](utm_|fbclid=|gclid=|ref=|campaign=|tracking=)',
    
    # Specific tracking domains
    r'doubleclick\.net',
    r'google-analytics\.com',
    r'googletagmanager\.com',
    r'googlesyndication\.com',
    r'facebook\.com/tr/',
    r'connect\.facebook\.net',
    r'twitter\.com/i/',
    r'ads\.pubmatic\.com',
    r'securepubads\.g\.doubleclick\.net',
    r'tpc\.googlesyndication\.com',
    r'fundingchoicesmessages\.google\.com',
    r'imasdk\.googleapis\.com',
    r'\.2mdn\.net',
    r'adnxs\.com',
    r'rubiconproject\.com',
    r'criteo\.com',
    r'smilewanted\.com',
    r'smartadserver\.com',
    r'admatic\.',
    r'adtrafficquality\.',
    r'omnitagjs\.com',
    r'innity\.com',
    r'eskimi\.com',
]

compiled_skip_patterns = [re.compile(pattern, re.IGNORECASE) for pattern in unncsry]
get_url = re.compile(r'(GET|POST)\s+(https?://\S+)')
cek_url = set()

def should_skip_url(url):
    return any(pattern.search(url) for pattern in compiled_skip_patterns)

def valid_url(url, line):
    if not url or not isinstance(url, str):
        return False
    if not (url.startswith("http") or url.startswith("https")):
        return False
    if any(url.lower().endswith(ext) for ext in unncsry):
        return False
    if should_skip_url(url):
        return False
    if 'text/html' not in line:
        return False
    return True

def extract_url_from_log(line):
    if ' CONNECT ' in line:
        return None
    match = get_url.search(line)
    if match:
        return match.group(2).split()[0]
    return None

def extract_deviceipaddress(line):
    parts = line.split()
    if len(parts) >= 3:
        return parts[2]
    return None

# Format durasi untuk display readable
def duration_format(seconds):
    if seconds < 60:
        return f"{int(seconds)}s"
    elif seconds < 3600:
        minutes = int(seconds // 60)
        seconds = int(seconds % 60)
        return f"{minutes}m {seconds}s"
    else:
        hours = int(seconds // 3600)
        minutes = int((seconds % 3600) // 60)
        seconds = int(seconds % 60)
        return f"{hours}h {minutes}m {seconds}s"

# Format durasi HH:MM:SS untuk dashboard
def duration_format_hms(seconds):
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    seconds = int(seconds % 60)
    return f"{hours:02d}:{minutes:02d}:{seconds:02d}"

def get_connection_duration(first_connected):
    current_time = time.time()
    return current_time - first_connected

def device_activity(ip, url):
    global connected_devices
    current_time = time.time()
    domain = extract_base(url)

    with device_lock:
        if ip not in connected_devices:
            connected_devices[ip] = {
                'first_connected': current_time,
                'last_connected': current_time,
                'request_count': 1,  # Start with 1 since this is the first request
                'domains': {domain} if domain else set(),
                'status': 'active'
            }
            # Immediately save new devices
            update_device_info_to_gsheet(ip, connected_devices[ip])
        else:
            # Update existing device
            connected_devices[ip]['last_connected'] = current_time
            connected_devices[ip]['request_count'] += 1
            connected_devices[ip]['status'] = 'active'
            if domain:
                connected_devices[ip]['domains'].add(domain)
            # Update in Google Sheets (with rate limiting)
            if time.time() - connected_devices[ip].get('last_sync', 0) > 60:  # Sync at most once per minute
                update_device_info_to_gsheet(ip, connected_devices[ip])
                connected_devices[ip]['last_sync'] = time.time()

def update_device_info_to_gsheet(ip, device_info):
    MAX_RETRIES = 3
    retry_count = 0
    
    while retry_count < MAX_RETRIES:
        try:
            domains_str = ', '.join(device_info['domains']) if device_info['domains'] else "-"
            
            row_data = [
                ip,
                datetime.datetime.fromtimestamp(device_info['first_connected']).strftime('%Y-%m-%d %H:%M:%S'),
                datetime.datetime.fromtimestamp(device_info['last_connected']).strftime('%Y-%m-%d %H:%M:%S'),
                str(round(time.time() - device_info['first_connected'], 2)),  # Duration in seconds
                str(device_info['request_count']),
                domains_str,
                device_info['status'],
                f"Device-{ip}"  # Default device name
            ]
            
            # Try to find existing device
            try:
                cell = sheet_devices.find(ip)
                if cell:
                    # Update existing row
                    sheet_devices.update(f'A{cell.row}:H{cell.row}', [row_data])
                    print(f"Updated device {ip} in Google Sheets")
                else:
                    # Append new row
                    sheet_devices.append_row(row_data)
                    print(f"Added new device {ip} to Google Sheets")
                return True
            except Exception as e:
                print(f"Error finding/updating device {ip}: {str(e)}")
                return False
                
        except gspread.exceptions.APIError as e:
            print(f"Google Sheets API error (retry {retry_count + 1}): {str(e)}")
            retry_count += 1
            time.sleep(5)  # Wait before retrying
        except Exception as e:
            print(f"Unexpected error saving device {ip}: {str(e)}")
            return False
    
    print(f"Failed to save device {ip} after {MAX_RETRIES} attempts")
    return False

def inactive_devices():
    global connected_devices
    current_time = time.time()
    with device_lock:
        inactive_list = []
        for ip, info in connected_devices.items():
            if current_time - info['last_connected'] > device_timeout:
                inactive_list.append(ip)
        
        for ip in inactive_list:
            connected_devices[ip]['status'] = 'inactive'

def active_devices():
    inactive_devices()
    with device_lock:
        return {ip: info for ip, info in connected_devices.items() if info['status'] == 'active'}

def extract_base(url):
    domain = url.split('//')[-1].split('/')[0]
    parts = domain.split('.')
    if len(parts) >= 3 and parts[-2] in ['co', 'ac', 'go', 'mil', 'or', 'web', 'sch', 'net']:
        return parts[-3]
    elif len(parts) >= 2:
        return parts[-2]
    return parts[0]

def determine_domain_category(domain):
    for category, domain_list in DOMAIN_CATEGORIES.items():
        if domain.lower() in map(str.lower, domain_list):
            return category
    for category, domain_list in DOMAIN_CATEGORIES.items():
        for d in domain_list:
            if domain.lower().endswith("." + d.lower()) or domain.lower() == d.lower():
                if d.lower().startswith("www.") and not domain.lower().startswith("www."):
                    if domain.lower().endswith(d.lower().replace("www.", ".")):
                        return category
                elif not d.lower().startswith("www.") and domain.lower().startswith("www."):
                     if domain.lower().replace("www.", "").endswith("." + d.lower()) or domain.lower().replace("www.", "") == d.lower():
                        return category
                else:
                    return category
    return "Dilarang_dikunjungi"

def url_check(url):
    base = extract_base(url)
    if base in blokir_kominfo:
        return [(base, 100, "Website Diblokir Kominfo")]
    for category, domain_list in DOMAIN_CATEGORIES.items():
        if base.lower() in map(str.lower, domain_list):
            return [(base, 100, category)]
    return []

def scrape_website(url):
    try:
        headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'}
        res = requests.get(url, headers=headers, timeout=10)
        soup = BeautifulSoup(res.text, 'html.parser')
        for tag in ['script', 'style', 'noscript', 'head', 'meta', 'link']:
            [e.decompose() for e in soup.find_all(tag)]
        return soup.get_text(separator=' ', strip=True)
    except Exception as e:
        return f"Error scraping website: {str(e)}"

def preprocess_text(text):
    text = re.sub(r'\s+', ' ', text.lower()).strip()
    stemmed = stemmer.stem(text)
    doc = nlp(stemmed)
    words = [t.text for t in doc if t.is_alpha and len(t.text) > 2]
    return {'words': words}

def check_negative_keywords(words, keyword_list):
    return [word for word in words if word in keyword_list]

def analyze_website_keyword(url, is_dangerous=False, matching_domain=None, category=None):
    is_alert = False
    alert_type = ""

    global total_alerts
    domain_category = category if is_dangerous else "Aman"
    raw_text = scrape_website(url)
    content_extracted = not raw_text.startswith("Error scraping")
    processed = preprocess_text(raw_text) if content_extracted else {'words': []}

    detected = {
        'pornografi': check_negative_keywords(processed['words'], pornografi),
        'judi': check_negative_keywords(processed['words'], judi_online),
        'kasar': check_negative_keywords(processed['words'], kata_kasar)
    }

    kategori_konten = [k for k, v in detected.items() if v]
    konten_status = "Mengandung konten negatif" if kategori_konten else "Aman"

    is_alert = False
    if is_dangerous:
        status = f"[DANGER] Berbahaya (domain diblokir - {category})"
        alert_type = "Website Diblokir"
        is_alert = True
    elif kategori_konten:
        status = f"[WARNING] Aman tapi mengandung konten negatif ({', '.join(kategori_konten)})"
        alert_type = "Website Aman Mengandung Konten Negatif"
        is_alert = True
    else:
        status = "[SAFE] Aman"

    if is_alert:
        with alert_lock:
            total_alerts += 1
            print(f"Alert terdeteksi pada {url}. Total Alerts: {total_alerts}")
            alert_entry = {
                "timestamp": datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                "url": url,
                "alert_type": alert_type,
                "domain_category_reason": domain_category,
                "kategori_konten_terdeteksi": kategori_konten,
                "detected_keywords": detected
            }
            try:
                save_alert_to_gsheet(alert_entry)
                print(f"Alert saved to database: {url}")
            except Exception as e:
                print(f"Error saving alert to database: {str(e)}")
            with alerts_history_lock:
                alerts_history.append(alert_entry)

    return {
        "url": url,
        "status": status,
        "domain_status": "Berbahaya" if is_dangerous else "Aman",
        "domain_category_reason": domain_category if is_dangerous else "-",
        "konten_status": konten_status,
        "kategori_konten_terdeteksi": kategori_konten,
        "content_extracted": content_extracted,
        "raw_text_preview": raw_text[:200] + "..." if content_extracted else raw_text,
        "detected_keywords": detected
    }

def check_website(url):
    status = url_check(url)
    if status:
        base, _, category = status[0]
        return analyze_website_keyword(url, True, base, category)
    else:
        return analyze_website_keyword(url, False)

def internetmonitoring_flask():
    global web_activity_history
    if not os.path.exists(squid_logs):
        print(f"Log file tidak ditemukan: {squid_logs}")
        return

    print(f"Internet Monitoring dimulai. Log file: {squid_logs}")
    
    with open(squid_logs, "r", encoding='utf-8', errors="ignore") as logs:
        logs.seek(0, os.SEEK_END)
        try:
            while True:
                where = logs.tell()
                line = logs.readline()
                if not line:
                    time.sleep(0.2)  
                    logs.seek(where)
                else:
                    url = extract_url_from_log(line)
                    ip = extract_deviceipaddress(line)
                    if url and valid_url(url, line) and url not in cek_url:
                        cek_url.add(url)
                        if ip:
                            device_activity(ip, url)
                        result = check_website(url)
                        print(result)

                        with web_activity_lock:
                            log_entry = {
                                "timestamp": datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                                "device_ip": ip,
                                "url": url,
                                "domain": extract_base(url),
                                "status": result["status"],
                                "domain_status": result["domain_status"],
                                "domain_category": result.get("domain_category_reason", "-"),
                                "content_status": result["konten_status"],
                                "detected_content": result["kategori_konten_terdeteksi"],
                                "is_safe": result["domain_status"] == "Aman" and result["konten_status"] == "Aman"
                            }
                            web_activity_history.append(log_entry)
                            try:
                                save_web_activity_log_to_gsheet(log_entry)
                                print(f"Web activity saved to database: {url}")
                            except Exception as e:
                                print(f"Error saving web activity to database: {str(e)}")
                        if len(web_activity_history) > 1000:
                            web_activity_history = web_activity_history[-1000:]
        except Exception as e:
            print(f"Error dalam monitoring: {str(e)}")


# SAVE
def save_web_activity_log_to_gsheet(log):
    try:
        row_data = [
            log['timestamp'],
            log['device_ip'],
            log['url'],
            log['domain'],
            log['status'],
            log['domain_status'],
            log['domain_category'],
            log['content_status'],
            ', '.join(log['detected_content']),
            "TRUE" if log['is_safe'] else "FALSE"
        ]
        if len(row_data) != 10:
            print(f"[GSHEET] Warning: Jumlah kolom tidak sesuai di web logs: {len(row_data)}. Data: {row_data}")
        sheet_web_logs.append_row(row_data)
        return True
    except Exception as e:
        print(f"[GSHEET] Gagal simpan log web: {e}")
        return False


def save_alert_to_gsheet(alert):
    try:
        row_data = [
            alert['timestamp'],
            alert['url'],
            alert['alert_type'],
            alert.get('domain_category_reason', '-'),
            ', '.join(alert.get('kategori_konten_terdeteksi', [])),
            str(alert.get('detected_keywords', {}))
        ]
        if len(row_data) != 6:
            print(f"[GSHEET] Warning: Jumlah kolom tidak sesuai di alerts: {len(row_data)}. Data: {row_data}")
        sheet_alerts.append_row(row_data)
        return True
    except Exception as e:
        print(f"[GSHEET] Gagal simpan alert: {e}")
        return False


# ENDPOINTS
@app.route("/total_alerts", methods=["GET"])
def get_total_alerts():
    global total_alerts
    return jsonify({"total_alerts": total_alerts})

@app.route("/connection_time", methods=["GET"])
def get_connection_time():
    active_devs = active_devices()
    if not active_devs:
        return jsonify({
            "connection_time": "00:00:00",
            "connection_time_formatted": "0s"
        })
    longest_connection = max(
        (time.time() - info['first_connected'] for info in active_devs.values()),
        default=0
    )
    return jsonify({
        "connection_time": duration_format_hms(longest_connection),
        "connection_time_formatted": duration_format(longest_connection)
    })

@app.route("/connected_devices", methods=["GET"])
def get_connected_devices():
    active_devs = active_devices()
    
    devices_info = {}
    for ip, info in active_devs.items():
        connection_duration_seconds = get_connection_duration(info['first_connected'])
        devices_info[ip] = {
            'first_connected': datetime.datetime.fromtimestamp(info['first_connected']).strftime('%Y-%m-%d %H:%M:%S'),
            'last_connected': datetime.datetime.fromtimestamp(info['last_connected']).strftime('%Y-%m-%d %H:%M:%S'),
            'connection_duration_seconds': round(connection_duration_seconds, 0),
            'connection_duration_formatted': duration_format(connection_duration_seconds),
            'request_count': info['request_count'],
            'domains_visited': list(info['domains']),
            'domains_count': len(info['domains']),
            'status': info['status']
        }
    
    return jsonify({
        "total_devices": len(active_devs),
        "devices": devices_info
    })

@app.route("/device_count", methods=["GET"])
def get_device_count():
    active_devs = active_devices()
    return jsonify({"device_count": len(active_devs)})

@app.route("/device_summary", methods=["GET"])
def get_device_summary():
    active_devs = active_devices()
    total_requests = sum(info['request_count'] for info in active_devs.values())

    return jsonify({
        "total_active_devices": len(active_devs),
        "total_requests": total_requests,
        "total_alerts": total_alerts,
    })

@app.route("/alerts_info", methods=["GET"])
def get_alerts_info():
    with alerts_history_lock:
        alerts_copy = alerts_history.copy()
    blocked_websites = []
    negative_content_websites = []
    for alert in alerts_copy:
        if alert["alert_type"] == "Website Diblokir":
            blocked_websites.append({
                "url": alert["url"],
                "status": "Website Diblokir",
                "category": alert["domain_category_reason"],
                "timestamp": alert["timestamp"],
                "detected_keywords": alert["detected_keywords"]
            })
        elif alert["alert_type"] == "Website Aman Mengandung Konten Negatif":
            negative_content_websites.append({
                "url": alert["url"],
                "status": "Website Aman Mengandung Konten Negatif",
                "kategori_konten": alert["kategori_konten_terdeteksi"],
                "timestamp": alert["timestamp"],
                "detected_keywords": alert["detected_keywords"]
            })
    
    blocked_websites.sort(key=lambda x: x["timestamp"], reverse=True)
    negative_content_websites.sort(key=lambda x: x["timestamp"], reverse=True)
    
    total_blocked = len(blocked_websites)
    total_negative = len(negative_content_websites)
    total_all_alerts = total_blocked + total_negative
    
    last_updated = "N/A"
    if alerts_copy:
        latest_alert = max(alerts_copy, key=lambda x: x["timestamp"])
        last_updated = latest_alert["timestamp"]
    
    return jsonify({
        "total_alerts": total_all_alerts,
        "blocked_websites": {
            "count": total_blocked,
            "websites": blocked_websites
        },
        "negative_content_websites": {
            "count": total_negative,
            "websites": negative_content_websites
        },
        "summary": {
            "total_blocked_websites": total_blocked,
            "total_negative_content_websites": total_negative,
            "last_updated": last_updated
        }
    })

@app.route("/web_activity_logs", methods=["GET"])
def get_web_activity_logs():
    limit = request.args.get('limit', 100, type=int)
    device_ip_filter = request.args.get('device_ip')
    status_filter = request.args.get('status')
    hours_filter = request.args.get('hours', type=int)
    with web_activity_lock:
        logs_copy = web_activity_history.copy()
    logs_copy.sort(key=lambda x: x['timestamp'], reverse=True)
    logs_copy = logs_copy[:limit]
    total_logs = len(web_activity_history)
    safe_count = sum(1 for log in logs_copy if log['is_safe'])
    unsafe_count = len(logs_copy) - safe_count
    
    devices_summary = {}
    for log in logs_copy:
        ip = log['device_ip']
        if ip not in devices_summary:
            devices_summary[ip] = {
                'total_requests': 0,
                'safe_requests': 0,
                'unsafe_requests': 0,
                'domains_visited': set()
            }
        devices_summary[ip]['total_requests'] += 1
        devices_summary[ip]['domains_visited'].add(log['domain'])
        if log['is_safe']:
            devices_summary[ip]['safe_requests'] += 1
        else:
            devices_summary[ip]['unsafe_requests'] += 1
    
    for device_info in devices_summary.values():
        device_info['domains_visited'] = list(device_info['domains_visited'])
        device_info['unique_domains_count'] = len(device_info['domains_visited'])
    
    return jsonify({
        "summary": {
            "total_logs_in_history": total_logs,
            "logs_returned": len(logs_copy),
            "safe_requests": safe_count,
            "unsafe_requests": unsafe_count,
            "last_updated": datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            "filters_applied": {
                "limit": limit,
                "device_ip": device_ip_filter,
                "status": status_filter,
                "hours": hours_filter
            }
        },
        "devices_summary": devices_summary,
        "logs": logs_copy
    })

@app.route("/dashboard", methods=["GET"])
def get_dashboard():
    global total_alerts
    active_devs = active_devices()
    total_devices = len(active_devs)

    if not active_devs:
        connection_time = "00:00:00"
    else:
        longest_connection = max(
            (time.time() - info['first_connected'] for info in active_devs.values()),
            default=0
        )
        connection_time = duration_format_hms(longest_connection)

    return jsonify({
        "total_devices": total_devices,
        "total_alerts": total_alerts,
        "connection_time": connection_time,
        "last_updated": datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    })

if __name__ == "__main__":
    print("Google Sheet integration enabled. Starting monitoring...")    
    # Start monitoring thread
    thread = threading.Thread(target=internetmonitoring_flask, name="MonitoringThread")
    thread.daemon = True
    thread.start()
    print("Monitoring started automatically.")
    
    # Start Flask app
    app.run(host="0.0.0.0", port=5000)
