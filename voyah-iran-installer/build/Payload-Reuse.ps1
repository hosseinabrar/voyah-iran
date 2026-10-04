# Reuse only a recent, unchanged payload across VS Build and Publish invocations.
function Get-VoyahPayloadFingerprint([string]$AndroidRoot,[string]$InstallerRoot,[string]$Identity) {
    $files=New-Object 'System.Collections.Generic.List[System.IO.FileInfo]'
    function Add-InputTree([string]$Directory) {
        if (-not (Test-Path -LiteralPath $Directory)) { return }
        foreach ($item in Get-ChildItem -LiteralPath $Directory -Force) {
            if ($item.PSIsContainer) {
                if ($item.Name -notin @('build','obj','.gradle','.kotlin','.idea','.git','__pycache__') -and
                    $item.FullName -ne [IO.Path]::GetFullPath((Join-Path $AndroidRoot 'app/release')) -and $item.FullName -ne [IO.Path]::GetFullPath((Join-Path $AndroidRoot 'app/debug'))) { Add-InputTree $item.FullName }
            } else { $files.Add($item) }
        }
    }
    Get-ChildItem -LiteralPath $AndroidRoot -File | ForEach-Object { $files.Add($_) }
    foreach($dir in @('app','raha-core','raha-feedback','raha-runtime','gradle','Keystore','deployment')) { Add-InputTree (Join-Path $AndroidRoot $dir) }
    Add-InputTree (Join-Path $InstallerRoot 'tools')
    Get-ChildItem -LiteralPath (Join-Path $InstallerRoot 'build') -File | Where-Object { $_.Extension -in @('.ps1','.py','.cmd','.bat') -or $_.Name -eq 'BuildSettings.local.json' } | ForEach-Object { $files.Add($_) }
    $lines=@($Identity)
    $lines+=@($files | Sort-Object FullName | ForEach-Object { $_.FullName+'|'+$_.Length+'|'+$_.LastWriteTimeUtc.Ticks })
    $sha=[Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")))).Replace('-','') }
    finally { $sha.Dispose() }
}
function Test-VoyahPayloadReuse([string]$Receipt,[string]$Bundle,[string]$Fingerprint) {
    if (-not (Test-Path -LiteralPath $Receipt) -or -not (Test-Path -LiteralPath $Bundle)) { return $false }
    try {
        $record=Get-Content -LiteralPath $Receipt -Raw | ConvertFrom-Json
        $age=[DateTime]::UtcNow - [DateTime]::Parse($record.CreatedUtc).ToUniversalTime()
        $file=Get-Item -LiteralPath $Bundle
        return $age.TotalSeconds -ge 0 -and $age.TotalMinutes -lt 10 -and $record.Fingerprint -eq $Fingerprint -and
            $record.Length -eq $file.Length -and $record.ModifiedTicks -eq $file.LastWriteTimeUtc.Ticks
    } catch { return $false }
}
function Save-VoyahPayloadReuse([string]$Receipt,[string]$Bundle,[string]$Fingerprint) {
    $file=Get-Item -LiteralPath $Bundle
    @{Fingerprint=$Fingerprint;CreatedUtc=[DateTime]::UtcNow.ToString('o');Length=$file.Length;ModifiedTicks=$file.LastWriteTimeUtc.Ticks} |
        ConvertTo-Json | Set-Content -LiteralPath $Receipt -Encoding UTF8
}
