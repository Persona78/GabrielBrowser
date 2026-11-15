@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   Publicar Gabriel Browser no GitHub
echo ============================================
echo.

where git >nul 2>&1
if errorlevel 1 (
  echo ERRO: o Git nao esta instalado ou nao esta no PATH.
  pause
  exit /b 1
)

if not exist ".git" (
  echo ERRO: esta pasta nao e um repositorio Git. Coloca este ficheiro na raiz do projeto.
  pause
  exit /b 1
)

rem Nunca enviar APK/AAB para o repositorio (o GitHub recusa mais de 100 MB)
set TRACKED=
for /f "delims=" %%f in ('git ls-files "*.apk" "*.aab"') do set TRACKED=%%f
if defined TRACKED (
  echo ERRO: ha um APK/AAB no repositorio: %TRACKED%
  echo Remove-o com: git rm --cached "%TRACKED%"   e confirma que o .gitignore ignora *.apk
  pause
  exit /b 1
)

rem Versao lida do app\build.gradle.kts
set VER=
for /f "usebackq delims=" %%v in (`powershell -NoProfile -Command "(Select-String -Path 'app\build.gradle.kts' -Pattern 'versionName\s*=\s*.?([0-9][^\x22\s]*)').Matches[0].Groups[1].Value"`) do set VER=%%v
if not defined VER set VER=0.0
echo Versao da app: %VER%
echo.

set MSG=
set /p MSG=Mensagem do commit (Enter = "Atualizacao"): 
if not defined MSG set MSG=Atualizacao

git add -A
git commit -m "%MSG%"
if errorlevel 1 echo (Nada de novo para guardar num commit, a continuar...)

echo.
echo A enviar para o GitHub...
git push
if errorlevel 1 (
  echo.
  echo ERRO no envio. Verifica a mensagem acima.
  pause
  exit /b 1
)

echo.
set REL=
set /p REL=Criar/atualizar a Release v%VER% com o APK? (S/N): 
if /i not "%REL%"=="S" goto :fim

where gh >nul 2>&1
if errorlevel 1 (
  echo.
  echo O GitHub CLI nao esta instalado. Instala com:  winget install GitHub.cli
  echo Depois abre uma nova janela e corre:  gh auth login
  echo Em alternativa, cria a Release no site do GitHub e arrasta o APK.
  goto :fim
)

set APK=app\release\GabrielBrowser.apk
if not exist "%APK%" (
  echo.
  echo APK nao encontrado em %APK%
  set /p APK=Caminho completo do APK: 
)
if not exist "%APK%" (
  echo APK nao encontrado. Release cancelada.
  goto :fim
)

gh release view v%VER% >nul 2>&1
if errorlevel 1 (
  gh release create v%VER% "%APK%" --title "Gabriel Browser %VER%" --notes "Gabriel Browser %VER%. Instala por cima da versao anterior (mesma chave de assinatura)."
) else (
  echo A Release v%VER% ja existe: a substituir o APK.
  gh release upload v%VER% "%APK%" --clobber
)

:fim
echo.
echo Concluido.
pause
