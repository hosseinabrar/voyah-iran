$ErrorActionPreference='Stop'
$source=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../build'))
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('voyah-build-settings-test-'+[guid]::NewGuid().ToString('N'))
$builder=Join-Path $fixture 'installer/build'
$android=Join-Path $fixture 'voyah-iran'
function Assert($condition,$message) { if(-not $condition) { throw $message } }
try {
    foreach($dir in @($builder,"$android/Keystore","$android/app/build/outputs/apk/release","$android/deployment/protection","$android/build/raha-protected/old","$fixture/jdk/bin","$fixture/installer/payload","$fixture/installer/tools")) {
        [void][IO.Directory]::CreateDirectory($dir)
    }
    Copy-Item "$source/Publish-Android.ps1","$source/Build-Settings.ps1","$source/Payload-Reuse.ps1" $builder
    Set-Content "$android/Keystore/voyahiran" 'dummy key'
    Set-Content "$fixture/jdk/bin/javac.exe" 'dummy JDK'
    Set-Content "$android/app/build/outputs/apk/release/app-release.apk" 'fresh signed APK'
    Set-Content "$android/gradlew.bat" "@echo off`r`nexit /b 0" -Encoding ASCII
    Set-Content "$builder/Prepare-Payload.ps1" 'param($Apk,$Mapping,$ProtectedRun,[switch]$FastCompression) @{Apk=$Apk;Mapping=$Mapping;ProtectedRun=$ProtectedRun;FastCompression=[bool]$FastCompression}|ConvertTo-Json|Set-Content (Join-Path $PSScriptRoot "selection.json"); Set-Content (Join-Path $PSScriptRoot "../payload/bundle.zip") "test bundle"'
    Set-Content "$android/deployment/protection/Protect-Raha.ps1" 'param($Apk,$JavaHome,$PythonExe) [void][IO.Directory]::CreateDirectory((Join-Path $PSScriptRoot "../../build/raha-protected/new"))'
    Set-Content "$android/deployment/protection/Sign-Raha.ps1" 'param($RunDirectory,$Keystore,$Alias,[switch]$UseEnvironmentPasswords) if(-not $UseEnvironmentPasswords -or -not $env:VI_DPT_STORE_PASSWORD){throw "No signing credentials"}'
    . "$builder/Build-Settings.ps1"
    $settingsPath=Join-Path $builder 'BuildSettings.local.json'
    Assert (Get-VoyahBuildSettings $settingsPath).ProtectionEnabled 'Protection must default on'
    Save-VoyahBuildSettings $settingsPath $false 'dummy-password-test-only' ''
    $settings=Get-VoyahBuildSettings $settingsPath
    Assert ((Read-VoyahBuildPassword $settings.StorePassword) -eq 'dummy-password-test-only') 'Password roundtrip failed'
    Assert ((Read-VoyahBuildPassword $settings.KeyPassword) -eq 'dummy-password-test-only') 'Blank key password fallback failed'
    Assert (-not (Get-Content $settingsPath -Raw).Contains('dummy-password-test-only')) 'Plaintext credential persisted'
    $before=$env:VI_DPT_STORE_PASSWORD
    & "$builder/Publish-Android.ps1" -JavaHome "$fixture/jdk" -PreparePayload
    $selection=Get-Content "$builder/selection.json" -Raw|ConvertFrom-Json
    Assert ($selection.Apk.EndsWith('app-release.apk') -and $selection.Mapping -and -not $selection.ProtectedRun) 'Unprotected selected stale protected APK'
    Assert (-not (Test-Path "$android/build/raha-protected/new")) 'Protection ran while disabled'
    $selectionTime=(Get-Item "$builder/selection.json").LastWriteTimeUtc
    & "$builder/Publish-Android.ps1" -JavaHome "$fixture/jdk" -PreparePayload -ReuseUnchanged
    Assert ((Get-Item "$builder/selection.json").LastWriteTimeUtc -eq $selectionTime) 'Identical publish rebuilt payload'
    . "$builder/Payload-Reuse.ps1"
    $fp=Get-VoyahPayloadFingerprint $android "$fixture/installer" 'test'
    Set-Content "$android/new-input.txt" 'added source'
    $changed=Get-VoyahPayloadFingerprint $android "$fixture/installer" 'test'
    Assert ($changed -ne $fp) 'New source did not invalidate cache'
    Remove-Item "$android/new-input.txt"
    Assert ((Get-VoyahPayloadFingerprint $android "$fixture/installer" 'test') -eq $fp) 'Deleted source was not reflected'
    Assert ($env:VI_DPT_STORE_PASSWORD -eq $before) 'Credential environment was not restored'
    Save-VoyahBuildSettings $settingsPath $false 'dummy-password-test-only' '' $false
    & "$builder/Publish-Android.ps1" -JavaHome "$fixture/jdk" -PreparePayload
    $selection=Get-Content "$builder/selection.json" -Raw|ConvertFrom-Json
    Assert ($selection.Apk -and -not $selection.Mapping -and $selection.FastCompression) 'Fast build must not use stale R8 mapping'
    $rejected=$false
    try { Save-VoyahBuildSettings $settingsPath $true 'dummy-password-test-only' '' $false } catch { $rejected=$true }
    Assert $rejected 'Protection without R8 must be rejected'
    $legacy=Get-Content $settingsPath -Raw|ConvertFrom-Json
    $legacy.PSObject.Properties.Remove('MinifyEnabled')
    $legacy|ConvertTo-Json|Set-Content $settingsPath
    Assert (Get-VoyahBuildSettings $settingsPath).MinifyEnabled 'Legacy settings must retain release defaults'
    Save-VoyahBuildSettings $settingsPath $true 'dummy-password-test-only' 'separate-key-test-only'
    & "$builder/Publish-Android.ps1" -JavaHome "$fixture/jdk" -PreparePayload
    $selection=Get-Content "$builder/selection.json" -Raw|ConvertFrom-Json
    Assert ($selection.ProtectedRun.EndsWith('new') -and -not $selection.Apk) 'Protected build did not pin current run'
    Set-Content "$android/gradlew.bat" "@echo off`r`nexit /b 7" -Encoding ASCII
    $failed=$false
    try { & "$builder/Publish-Android.ps1" -JavaHome "$fixture/jdk" -PreparePayload } catch { $failed=$true }
    Assert $failed 'Failed Gradle build was accepted'
    Assert ($env:VI_DPT_STORE_PASSWORD -eq $before) 'Failed build leaked credential environment'
    Write-Host 'PASS: encrypted settings, defaults, unprotected/protected routing, current-run selection, failed build and environment cleanup.'
} finally {
    $resolved=[IO.Path]::GetFullPath($fixture)
    $tempRoot=[IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if($resolved.StartsWith($tempRoot,[StringComparison]::OrdinalIgnoreCase) -and [IO.Path]::GetFileName($resolved).StartsWith('voyah-build-settings-test-')) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
