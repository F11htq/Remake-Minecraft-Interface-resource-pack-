@echo off
rem CYBER//UI: one-click update of the resource pack and FancyMenu files.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0update-cyber-ui.ps1"
pause
