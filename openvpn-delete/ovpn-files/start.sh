#!/bin/bash
# date 2020-02-04 21:08:08
# author calllivecn <calllivecn@outlook.com>

set -e

/ovpn-files/ovpn-init-ca-server-cert.sh

echo "user add is run: /ovpn-files/ovpn-useradd.sh <username>"

exec openvpn --config /etc/openvpn/server/server.ovpn
