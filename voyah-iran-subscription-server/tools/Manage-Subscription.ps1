param(
 [ValidateSet('List','Get','Block','Unblock')][string]$Action='List',
 [string]$Vin,
 [string]$Reason='',
 [string]$Server='http://127.0.0.1:8080',
 [string]$AdminTokenFile=(Join-Path $PSScriptRoot '../secrets/admin-token.txt')
)
$ErrorActionPreference='Stop'
if(([Uri]$Server).Scheme -notin @('http','https')){throw 'Use an HTTP or HTTPS server address.'}
$headers=@{Authorization='Bearer '+[IO.File]::ReadAllText((Resolve-Path -LiteralPath $AdminTokenFile)).Trim()}
$root=$Server.TrimEnd('/')+'/admin/subscriptions'
if($Action -eq 'List'){Invoke-RestMethod -Uri ($root+'/?limit=100') -Headers $headers;exit}
$vehicleVin=$Vin.Trim().ToUpperInvariant()
if($vehicleVin -notmatch '^[A-HJ-NPR-Z0-9]{17}$'){throw 'Invalid VIN'}
if($Action -eq 'Get'){Invoke-RestMethod -Uri ($root+'/'+$vehicleVin) -Headers $headers;exit}
$body=@{vin=$vehicleVin;blocked=($Action -eq 'Block');reason=$Reason}|ConvertTo-Json -Compress
Invoke-RestMethod -Method Post -Uri ($root+'/block') -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
