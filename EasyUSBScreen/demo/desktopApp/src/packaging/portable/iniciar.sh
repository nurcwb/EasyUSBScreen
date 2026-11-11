#!/bin/sh
set -eu
app_dir="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd -P)"
exec "$app_dir/bin/br.com.infonur.easyusbscreen" "$@"
