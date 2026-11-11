#!/bin/sh
set -eu
app_dir="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd -P)"
data_dir="${XDG_DATA_HOME:-${HOME:?HOME não definido}/.local/share}"
case "$data_dir" in
    /*) ;;
    *) printf '%s\n' 'XDG_DATA_HOME precisa ser um caminho absoluto.' >&2; exit 1 ;;
esac
case "$app_dir$data_dir" in
    *'
'*) printf '%s\n' 'O caminho não pode conter quebras de linha.' >&2; exit 1 ;;
esac
if [ ! -f "$app_dir/iniciar.sh" ] || [ ! -f "$app_dir/easyusbscreen.png" ]; then
    printf '%s\n' 'Extraia o pacote completo antes de instalar o atalho.' >&2
    exit 1
fi
# Desktop Entry has two escaping layers for quoted Exec arguments.
escape_exec() {
    printf '%s' "$1" | sed 's/\\/\\\\/g; s/["`$]/\\&/g; s/%/%%/g' | sed 's/\\/\\\\/g'
}
escape_value() {
    printf '%s' "$1" | sed 's/\\/\\\\/g; s/ /\\s/g; s/\t/\\t/g; s/\r/\\r/g'
}
applications="$data_dir/applications"
mkdir -p -- "$applications"
temporary="$(mktemp "$applications/.easyusbscreen-XXXXXX.desktop")"
local_temporary=''
trap 'rm -f -- "$temporary"; if [ -n "$local_temporary" ]; then rm -f -- "$local_temporary"; fi' EXIT HUP INT TERM
{
    printf '%s\n' '[Desktop Entry]' 'Version=1.0' 'Type=Application' 'Name=EasyUSBScreen' 'Comment=Gerenciar a tela USB e os temas do Turing'
    printf 'Exec=/bin/sh "%s"\n' "$(escape_exec "$app_dir/iniciar.sh")"
    printf 'Path=%s\n' "$(escape_value "$app_dir")"
    printf 'Icon=%s\n' "$(escape_value "$app_dir/easyusbscreen.png")"
    printf '%s\n' 'Terminal=false' 'Categories=System;Monitor;' 'StartupNotify=false'
} > "$temporary"
if command -v desktop-file-validate >/dev/null 2>&1; then
    desktop-file-validate "$temporary"
fi
chmod 755 "$temporary"
entry="$applications/br.com.infonur.easyusbscreen.desktop"
mv -f -- "$temporary" "$entry"
# Keep the launcher beside iniciar.sh usable with the same absolute icon/path.
if [ -w "$app_dir" ]; then
    local_temporary="$(mktemp "$app_dir/.launcher-XXXXXX")"
    cp -- "$entry" "$local_temporary"
    chmod 755 "$local_temporary"
    mv -f -- "$local_temporary" "$app_dir/EasyUSBScreen.desktop"
    local_temporary=''
fi
if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$applications" >/dev/null 2>&1 || true
fi
if [ -n "${DBUS_SESSION_BUS_ADDRESS:-}" ]; then
    for cache_tool in kbuildsycoca6 kbuildsycoca5; do
        if command -v "$cache_tool" >/dev/null 2>&1; then
            "$cache_tool" --noincremental >/dev/null 2>&1 || printf '%s\n' 'O KDE atualizará o menu na próxima sessão.' >&2
            break
        fi
    done
fi
printf 'Atalho criado: %s\n' "$entry"
printf '%s\n' 'Procure EasyUSBScreen no menu do KDE.' 'Se mover a pasta do aplicativo, execute instalar-atalho.sh novamente.'
