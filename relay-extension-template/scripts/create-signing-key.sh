#!/bin/sh
set -eu

umask 077
mkdir -p keys
openssl ecparam -name prime256v1 -genkey -noout -out keys/repository-private.pem
openssl ec -in keys/repository-private.pem -pubout -outform DER | base64 | tr -d '\n' > keys/repository-public-base64.txt
printf 'Created keys/repository-private.pem (never commit it).\n'
printf 'Copy keys/repository-public-base64.txt into repository.json and Relay Settings.\n'
