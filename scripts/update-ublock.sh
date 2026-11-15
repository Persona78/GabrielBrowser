#!/usr/bin/env bash
# Descarrega a última versão do uBlock Origin (Firefox) e coloca-a em app/src/main/assets/ublock.
# Corre-o no teu computador (precisa de rede e curl; para descompactar usa unzip, tar, python ou PowerShell, o que existir).
set -euo pipefail
cd "$(dirname "$0")/.."

DEST="app/src/main/assets/ublock"
API="https://api.github.com/repos/gorhill/uBlock/releases/latest"

api=$(curl -fsSL "$API") || { echo "Não consegui contactar o GitHub."; exit 2; }
url=$(printf '%s' "$api" | grep -o '"browser_download_url": *"[^"]*firefox[^"]*\.xpi"' | head -n1 | sed 's/.*"\(https[^"]*\)"/\1/')
[[ -n "$url" ]] || { echo "Não encontrei o ficheiro .xpi para Firefox na última release."; exit 2; }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
curl -fsSL -o "$tmp/ubo.xpi" "$url"

# Descompacta um .xpi (é um ZIP) com a primeira ferramenta que funcionar.
winpath() { cygpath -w "$1" 2>/dev/null || wslpath -w "$1" 2>/dev/null || printf '%s' "$1"; }
extract() {
  local zip="$1" out="$2"
  if command -v unzip >/dev/null 2>&1 && unzip -q "$zip" -d "$out" 2>/dev/null; then return 0; fi
  if command -v bsdtar >/dev/null 2>&1 && bsdtar -xf "$zip" -C "$out" 2>/dev/null; then return 0; fi
  if tar --version 2>/dev/null | grep -qi bsdtar && tar -xf "$zip" -C "$out" 2>/dev/null; then return 0; fi
  if command -v python3 >/dev/null 2>&1 && python3 -m zipfile -e "$zip" "$out" 2>/dev/null; then return 0; fi
  if command -v python >/dev/null 2>&1 && python -m zipfile -e "$zip" "$out" 2>/dev/null; then return 0; fi
  if command -v powershell.exe >/dev/null 2>&1; then
    cp "$zip" "$zip.zip"
    if powershell.exe -NoProfile -Command "Expand-Archive -Force -LiteralPath '$(winpath "$zip.zip")' -DestinationPath '$(winpath "$out")'" >/dev/null 2>&1; then
      return 0
    fi
  fi
  return 1
}

mkdir -p "$DEST"
old=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$DEST/manifest.json" 2>/dev/null | head -n1 || true)
find "$DEST" -mindepth 1 ! -name LEIAME.txt -delete
extract "$tmp/ubo.xpi" "$DEST" || {
  echo "Não consegui descompactar. Instala unzip, ou descompacta à mão o ficheiro:"
  echo "  $url"
  echo "para $DEST (o manifest.json fica na raiz dessa pasta)."
  exit 3
}
[[ -f "$DEST/manifest.json" ]] || { echo "manifest.json não ficou em $DEST (verifica a extração)."; exit 3; }

ver=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$DEST/manifest.json" | head -n1)
echo "uBlock Origin ${ver:-?} colocado em $DEST"
if [[ -n "$ver" && "$ver" != "$old" ]]; then
  # Versão nova: sobe o versionCode para o Android aceitar a instalação por cima.
  vc=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n1)
  if [[ -n "$vc" ]]; then
    sed -i "s/versionCode = $vc/versionCode = $((vc + 1))/" app/build.gradle.kts
    echo "versionCode $vc -> $((vc + 1)) (uBlock ${old:-nenhum} -> $ver)."
  fi
else
  echo "O uBlock Origin já estava na versão ${ver:-?}; versionCode inalterado."
fi
echo "Recompila a app e instala por cima (mesma chave de assinatura)."
