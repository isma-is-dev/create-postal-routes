#Requires -Version 5.1
<#
.SYNOPSIS
    Regenerates src/main/resources/icon.png (128x128 mod icon).

.DESCRIPTION
    The icon is drawn procedurally so the repository contains no third-party art.
    Colour choices mirror the in-game palette: dark slate map background, postal amber
    routes and a cream envelope.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$projectRoot = Split-Path -Parent $PSScriptRoot
$output = Join-Path $projectRoot 'src\main\resources\icon.png'
New-Item -ItemType Directory -Force -Path (Split-Path $output) | Out-Null

$size = 128
$bitmap = New-Object System.Drawing.Bitmap($size, $size)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.Color]::FromArgb(255, 16, 20, 26))

# Subtle vignette / map texture
$random = New-Object System.Random 7
for ($i = 0; $i -lt 220; $i++) {
    $x = $random.Next(0, $size)
    $y = $random.Next(0, $size)
    $alpha = $random.Next(4, 16)
    $graphics.FillRectangle((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($alpha, 232, 170, 68))), $x, $y, 1, 1)
}

# Border
$borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 58, 68, 82), 3)
$graphics.DrawRectangle($borderPen, 1.5, 1.5, $size - 4, $size - 4)

# Postal route (dashed) behind the envelope
$routePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(230, 232, 170, 68), 3)
$routePen.DashPattern = @(2.2, 1.6)
$graphics.DrawLine($routePen, 12, 104, 116, 104)
$routePen.Dispose()

# Route nodes
$nodeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 217, 138))
foreach ($node in @(@(20, 104), @(64, 104), @(108, 104))) {
    $graphics.FillEllipse($nodeBrush, ($node[0] - 4), ($node[1] - 4), 8, 8)
}
$nodeBrush.Dispose()

# Envelope body
$envelopeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 244, 231, 200))
$envelopeRect = New-Object System.Drawing.Rectangle(24, 34, 80, 52)
$graphics.FillRectangle($envelopeBrush, $envelopeRect)
$envelopeBrush.Dispose()

# Envelope flap (amber V)
$flapPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 196, 130, 40), 5)
$flapPen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
$points = @(
    (New-Object System.Drawing.PointF(29, 39)),
    (New-Object System.Drawing.PointF(64, 64)),
    (New-Object System.Drawing.PointF(99, 39))
)
$graphics.DrawLines($flapPen, $points)
$flapPen.Dispose()

# Stamp
$stampBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 226, 138, 42))
$graphics.FillRectangle($stampBrush, 86, 42, 12, 14)
$stampBrush.Dispose()

$graphics.Dispose()
$bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()

Write-Host "Wrote $output"
