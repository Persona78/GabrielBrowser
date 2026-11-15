#!/usr/bin/env bash
# Verifica a versão mais recente do GeckoView e (com --apply) atualiza o projeto.
#   ./scripts/update-geckoview.sh           -> só verifica (código de saída 10 = há atualização)
#   ./scripts/update-geckoview.sh --apply   -> escreve a nova versão em gradle.properties
#                                              e sobe o versionCode da app
set -euo pipefail
cd "$(dirname "$0")/.."

META_URL="https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/maven-metadata.xml"
APPLY=0
[[ "${1:-}" == "--apply" ]] && APPLY=1

meta=$(curl -fsSL "$META_URL") || { echo "Não consegui ler $META_URL (sem rede ou endereço mudou)."; exit 2; }

latest=$(printf '%s' "$meta" | sed -n 's:.*<release>\(.*\)</release>.*:\1:p' | head -n1)
if [[ -z "$latest" ]]; then
  latest=$(printf '%s' "$meta" | grep -o '<version>[^<]*</version>' | sed 's:</\?version>::g' | sort -V | tail -n1)
fi
[[ -n "$latest" ]] || { echo "Não encontrei nenhuma versão no metadata."; exit 2; }

current=$(sed -n 's/^geckoviewVersion=//p' gradle.properties | head -n1)
major=${latest%%.*}

if [[ "$current" == "$latest" ]]; then
  echo "GeckoView já está na versão mais recente ($latest)."
  exit 0
fi

echo "GeckoView: atual = ${current:-?}  ->  mais recente = $latest"

if [[ $APPLY -eq 0 ]]; then
  echo "Corre './scripts/update-geckoview.sh --apply' e volta a compilar."
  echo "Notas de segurança: https://www.mozilla.org/security/advisories/"
  exit 10
fi

sed -i "s/^geckoviewVersion=.*/geckoviewVersion=$latest/" gradle.properties

vc=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n1)
sed -i "s/versionCode = $vc/versionCode = $((vc + 1))/" app/build.gradle.kts
sed -i "s/versionName = \".*\"/versionName = \"1.0-gv$major\"/" app/build.gradle.kts

echo "Atualizado para $latest (versionCode $((vc + 1)))."
echo "Agora: compila, testa e instala por cima da versão anterior (com a mesma chave de assinatura)."
