# CYBER//UI updater: downloads the latest version from GitHub and installs the
# resource pack and the FancyMenu files into the Minecraft folder.
# Run it with update-cyber-ui.bat (double click).

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Repo   = "f11htq/remake-minecraft-interface-resource-pack-"
$Branch = "claude/minecraft-interface-boring-nen036"
$ZipUrl = "https://github.com/$Repo/archive/refs/heads/$Branch.zip"
$PackName = "CYBER-UI"
$PathFile = Join-Path $PSScriptRoot "cyber-ui-path.txt"

function Say($text, $color = "Gray") { Write-Host $text -ForegroundColor $color }

function Test-GameDir($dir) {
    return (Test-Path (Join-Path $dir "mods")) -and
           (Get-ChildItem (Join-Path $dir "mods") -Filter "*fancymenu*.jar" -ErrorAction SilentlyContinue)
}

function Find-GameDirs {
    $candidates = @(Join-Path $env:APPDATA ".minecraft")
    $roots = @(
        (Join-Path $env:USERPROFILE "curseforge\minecraft\Instances"),
        (Join-Path $env:APPDATA "ModrinthApp\profiles"),
        (Join-Path $env:APPDATA "PrismLauncher\instances"),
        (Join-Path $env:APPDATA ".tlauncher\legacy\Minecraft\game")
    )
    foreach ($root in $roots) {
        if (Test-Path $root) {
            $candidates += $root
            Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $candidates += $_.FullName
                $candidates += (Join-Path $_.FullName ".minecraft")
                $candidates += (Join-Path $_.FullName "minecraft")
            }
        }
    }
    return $candidates | Where-Object { Test-Path $_ } | Where-Object { Test-GameDir $_ } | Select-Object -Unique
}

function Get-GameDir {
    if (Test-Path $PathFile) {
        $saved = (Get-Content $PathFile -Raw).Trim()
        if ($saved -and (Test-Path $saved)) { return $saved }
    }
    $found = @(Find-GameDirs)
    if ($found.Count -eq 1) {
        $dir = $found[0]
    } elseif ($found.Count -gt 1) {
        Say "Найдено несколько папок игры с FancyMenu:" "Yellow"
        for ($i = 0; $i -lt $found.Count; $i++) { Say ("  [{0}] {1}" -f ($i + 1), $found[$i]) }
        $n = Read-Host "Введи номер нужной папки"
        $dir = $found[[int]$n - 1]
    } else {
        Say "Папка игры с FancyMenu не найдена автоматически." "Yellow"
        Say "Открой в игре: Настройки -> Пакеты ресурсов -> Открыть папку пакетов,"
        Say "поднимись на уровень выше и скопируй путь из адресной строки."
        $dir = (Read-Host "Вставь путь к папке игры").Trim('"', ' ')
    }
    if (-not (Test-Path (Join-Path $dir "mods"))) { throw "В папке '$dir' нет папки mods." }
    Set-Content -Path $PathFile -Value $dir -Encoding UTF8
    return $dir
}

function Copy-Dir($from, $to) {
    New-Item -ItemType Directory -Force -Path $to | Out-Null
    Copy-Item -Path (Join-Path $from "*") -Destination $to -Recurse -Force
}

try {
    Say "=== CYBER//UI: обновление ===" "Yellow"
    $game = Get-GameDir
    Say "Папка игры: $game"

    $tmp = Join-Path $env:TEMP ("cyber-ui-" + [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $tmp | Out-Null
    $zip = Join-Path $tmp "cyber-ui.zip"
    Say "Скачиваю последнюю версию с GitHub..."
    Invoke-WebRequest -Uri $ZipUrl -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $tmp -Force
    $src = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (-not $src) { throw "Не удалось распаковать архив." }
    $src = $src.FullName

    # Resource pack, installed as a folder (Minecraft loads folder packs too)
    $packDir = Join-Path $game "resourcepacks\$PackName"
    if (Test-Path $packDir) { Remove-Item $packDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $packDir | Out-Null
    Copy-Item (Join-Path $src "pack.mcmeta") $packDir -Force
    Copy-Item (Join-Path $src "pack.png") $packDir -Force
    Copy-Dir (Join-Path $src "assets") (Join-Path $packDir "assets")
    Say "Ресурс-пак обновлён: resourcepacks\$PackName" "Green"

    # FancyMenu files: remove our previous files, keep everything else
    $fm = Join-Path $game "config\fancymenu"
    $fmSrc = Join-Path $src "fancymenu-setup\config\fancymenu"
    New-Item -ItemType Directory -Force -Path $fm | Out-Null
    Get-ChildItem (Join-Path $fm "customization") -Filter "cyber_ui_*.txt" -ErrorAction SilentlyContinue | Remove-Item -Force
    if (Test-Path (Join-Path $fm "assets\cyber_ui")) { Remove-Item (Join-Path $fm "assets\cyber_ui") -Recurse -Force }
    Get-ChildItem (Join-Path $fm "panoramas") -Directory -Filter "cyber_*" -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
    Copy-Dir (Join-Path $fmSrc "customization") (Join-Path $fm "customization")
    Copy-Dir (Join-Path $fmSrc "assets") (Join-Path $fm "assets")
    Copy-Dir (Join-Path $fmSrc "panoramas") (Join-Path $fm "panoramas")
    Copy-Item (Join-Path $fmSrc "customizablemenus.txt") $fm -Force
    Say "Файлы FancyMenu обновлены: config\fancymenu" "Green"

    # Update this script too (the .bat is left alone: cmd is still running it)
    $newScript = Join-Path $src "update-cyber-ui.ps1"
    if (Test-Path $newScript) { Copy-Item $newScript (Join-Path $PSScriptRoot "update-cyber-ui.ps1") -Force }

    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    Say ""
    Say "Готово! Запусти игру (или нажми в игре: Кастомизация -> Перезагрузить FancyMenu)." "Yellow"
    Say "Если ресурс-пак ещё не включён: Настройки -> Пакеты ресурсов -> $PackName."
    Say "Старый архив CYBER-UI.zip в resourcepacks можно удалить, чтобы не было двух копий."
} catch {
    Say ""
    Say ("Ошибка: " + $_.Exception.Message) "Red"
}
