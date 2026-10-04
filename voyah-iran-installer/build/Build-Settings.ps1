# Local build preferences. Passwords use Windows DPAPI for the current user.
function Get-VoyahBuildSettings([string]$Path) {
    if (Test-Path -LiteralPath $Path) {
        $value = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        if ($value.ProtectionEnabled -isnot [bool]) { throw 'Invalid ProtectionEnabled in build settings.' }
        if (-not $value.PSObject.Properties['MinifyEnabled']) { $value | Add-Member NoteProperty MinifyEnabled $true }
        if ($value.MinifyEnabled -isnot [bool]) { throw 'Invalid MinifyEnabled in build settings.' }
        if ($value.ProtectionEnabled -and -not $value.MinifyEnabled) { throw 'Protection requires minification.' }
        return $value
    }
    return [pscustomobject]@{ ProtectionEnabled=$true; MinifyEnabled=$true; StorePassword=''; KeyPassword='' }
}
function Read-VoyahBuildPassword([string]$Encrypted) {
    if (-not $Encrypted) { return '' }
    $secret = ConvertTo-SecureString $Encrypted
    try { return [Net.NetworkCredential]::new('', $secret).Password } finally { $secret.Dispose() }
}
function Save-VoyahBuildSettings([string]$Path, [bool]$ProtectionEnabled, [string]$StorePassword, [string]$KeyPassword, [bool]$MinifyEnabled=$true) {
    if ($ProtectionEnabled -and -not $MinifyEnabled) { throw 'Protection requires minification.' }
    if (-not $StorePassword) { throw 'Keystore password is required.' }
    if (-not $KeyPassword) { $KeyPassword=$StorePassword }
    $storeSecret=ConvertTo-SecureString $StorePassword -AsPlainText -Force
    $keySecret=ConvertTo-SecureString $KeyPassword -AsPlainText -Force
    try {
        $settings=[ordered]@{ ProtectionEnabled=$ProtectionEnabled; MinifyEnabled=$MinifyEnabled; StorePassword=(ConvertFrom-SecureString $storeSecret); KeyPassword=(ConvertFrom-SecureString $keySecret) }
        $settings | ConvertTo-Json | Set-Content -LiteralPath "$Path.new" -Encoding UTF8
        Move-Item -LiteralPath "$Path.new" -Destination $Path -Force
    } finally { $storeSecret.Dispose(); $keySecret.Dispose() }
}
