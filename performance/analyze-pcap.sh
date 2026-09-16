#!/bin/sh
set -e

echo "Installing tshark (may take a moment)"
apt-get update >/dev/null
DEBIAN_FRONTEND=noninteractive apt-get install -y tshark >/dev/null 2>&1 || true

PCAP=/work/performance/swiftpay-load-test.pcap

echo "---- HTTP requests total ----"
tshark -r "$PCAP" -Y 'http.request' | wc -l

echo "---- POST /v1/payments ----"
tshark -r "$PCAP" -Y 'http.request.method == "POST" and http.request.uri contains "/v1/payments"' | wc -l

echo "---- HTTP response codes ----"
tshark -r "$PCAP" -Y 'http.response' -T fields -e http.response.code | sort | uniq -c || true

echo "---- TCP retransmissions ----"
tshark -r "$PCAP" -Y 'tcp.analysis.retransmission' | wc -l

echo "---- Top IP conversations ----"
tshark -r "$PCAP" -q -z conv,ip | sed -n '1,50p'

echo "---- Sample HTTP requests ----"
tshark -r "$PCAP" -Y 'http.request' -T fields -e frame.time -e ip.src -e ip.dst -e http.request.method -e http.request.full_uri | head -n 20
