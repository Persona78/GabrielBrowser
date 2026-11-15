@echo off
Title Update Gabriel Browser
pushd "%~dp0"
@echo [33mUpdating GeckoView[0m
bash scripts/update-geckoview.sh --apply
@echo.
@echo [33mUpdating uBlock Origin[0m
bash scripts/update-ublock.sh
@echo.
@echo Check the messages above (versionCode is raised automatically when something was updated).
popd
pause
exit