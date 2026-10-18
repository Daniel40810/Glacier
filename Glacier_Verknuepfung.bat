@echo off
rem Glacier: Verknuepfung "Glacier" mit Symbol auf dem Desktop und im Startmenue anlegen (Phase 16)
cd /d "%~dp0"
if not exist Glacier.ico (echo Glacier.ico fehlt im Projektordner. & pause & exit /b 1)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$w = New-Object -ComObject WScript.Shell; foreach ($d in @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs'))) { $s = $w.CreateShortcut((Join-Path $d 'Glacier.lnk')); $s.TargetPath = '%~dp0Glacier.bat'; $s.WorkingDirectory = '%~dp0'; $s.IconLocation = '%~dp0Glacier.ico'; $s.WindowStyle = 7; $s.Description = 'Glacier - Weisser Donner: der Perito Moreno als 3D-Szene'; $s.Save() }"
if errorlevel 1 (echo Die Verknuepfung liess sich nicht anlegen. & pause & exit /b 1)
echo Verknuepfung "Glacier" liegt auf dem Desktop und im Startmenue.
pause
