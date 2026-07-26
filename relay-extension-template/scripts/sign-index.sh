#!/bin/sh
set -eu

test -f keys/repository-private.pem || { printf 'Run scripts/create-signing-key.sh first.\n' >&2; exit 1; }
openssl dgst -sha256 -sign keys/repository-private.pem -out /tmp/relay-index.sig.der index.json
base64 < /tmp/relay-index.sig.der | tr -d '\n' > index.json.sig
rm /tmp/relay-index.sig.der
printf 'Wrote index.json.sig.\n'
