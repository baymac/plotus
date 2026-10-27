#!/bin/bash
# Run on the Mac, inside a local cmux terminal (not the SSH terminal).
set -euo pipefail

if [[ "$(uname -s)" != Darwin ]]; then
  echo 'Run this script on your Mac in a local cmux terminal.' >&2
  exit 1
fi
plotus_host=${1:?Usage: bash scripts/connect-mac.sh user@development-server}
plotus_socket=${CMUX_SOCKET_PATH:-}
if [[ -z "$plotus_socket" || ! -S "$plotus_socket" ]]; then
  for plotus_candidate in "$HOME/Library/Application Support/cmux/cmux.sock" /tmp/cmux.sock /tmp/cmux-debug.sock; do
    if [[ -S "$plotus_candidate" ]]; then
      plotus_socket=$plotus_candidate
      break
    fi
  done
fi
if [[ -z "$plotus_socket" || ! -S "$plotus_socket" ]]; then
  echo 'No local cmux socket found. Open a local terminal in the cmux Mac app and run again.' >&2
  exit 1
fi

# Use a fresh path so this never replaces another session's forward.
plotus_suffix=$(uuidgen | tr '[:upper:]' '[:lower:]')
plotus_remote_socket="/tmp/plotus-mac-${plotus_suffix}.sock"
echo "Forwarding the local cmux control socket through your authenticated SSH connection."
echo "Keep this terminal open. Ctrl-C disconnects it."
echo "Remote socket: $plotus_remote_socket"
echo "Once connected, paste that Remote socket line into the coding session."
exec ssh -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -NT -R "${plotus_remote_socket}:${plotus_socket}" "$plotus_host"
