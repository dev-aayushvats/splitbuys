#!/usr/bin/env bash
# One-time hardening for the droplet, run as root on the server:
#
#   ssh root@your.host 'bash -s' < deploy/server-setup.sh
#
# Does what was applied to production on 2026-11-07: a firewall (22, 80, 443 only), lower swappiness, services that
# are useless on a VM switched off, and a capped journal. Safe to run again.
#
# The firewall is the one step that can lock you out, so a dead-man switch is armed first: after 4 minutes UFW turns
# itself off again unless you cancel it. Open a SECOND ssh session, confirm it works, then cancel:
#   systemctl stop ufw-deadman.timer
set -euo pipefail

command -v ufw >/dev/null || { echo "ufw is not installed (apt install ufw)"; exit 1; }

echo "== firewall"
systemd-run --on-active=240 --unit=ufw-deadman /usr/sbin/ufw --force disable >/dev/null 2>&1 || true
echo "dead-man timer armed for 240s"
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow 22/tcp >/dev/null   # allow SSH BEFORE enabling, or this very session is cut off
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw --force enable
ufw status verbose | sed -n '1,14p'

echo "== memory"
# the default (60) pushes application pages to swap long before RAM is actually short
echo "vm.swappiness=10" > /etc/sysctl.d/99-splitbuys.conf
sysctl --system >/dev/null
echo "swappiness: $(cat /proc/sys/vm/swappiness)"
# multipath storage and firmware updates serve no purpose on a single-disk VM (about 35 MB between them)
systemctl disable --now multipathd.service multipathd.socket >/dev/null 2>&1 || true
systemctl mask --now fwupd.service >/dev/null 2>&1 || true
systemctl disable --now fwupd-refresh.timer >/dev/null 2>&1 || true
# a chatty journal is memory and disk on a small box
mkdir -p /etc/systemd/journald.conf.d
printf '[Journal]\nSystemMaxUse=50M\nRuntimeMaxUse=20M\n' > /etc/systemd/journald.conf.d/size.conf
systemctl restart systemd-journald

echo
echo "NEXT: from another terminal run 'ssh root@<host> true'. If it works, cancel the dead-man switch:"
echo "      systemctl stop ufw-deadman.timer"
