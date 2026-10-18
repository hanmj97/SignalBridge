# Phase 8 read-only API smoke check; no records or rules are changed.
$ErrorActionPreference = 'Stop'
$api = 'http://localhost:8081/api/v1/watches'

# GET /api/v1/watches - empty JSON array is a valid initial state.
$rulesResponse = Invoke-WebRequest -UseBasicParsing -Uri $api -Method Get
if ($rulesResponse.StatusCode -ne 200) { throw 'Watch rules endpoint did not return HTTP 200' }
$rules = ConvertFrom-Json -InputObject $rulesResponse.Content
Write-Host ('Watch rules endpoint OK. Item count: ' + @($rules).Count)

# GET /api/v1/watches/events - events appear only after a new/updated version is matched.
$eventsResponse = Invoke-WebRequest -UseBasicParsing -Uri "$api/events" -Method Get
if ($eventsResponse.StatusCode -ne 200) { throw 'Watch events endpoint did not return HTTP 200' }
$events = ConvertFrom-Json -InputObject $eventsResponse.Content
Write-Host ('Watch events endpoint OK. Item count: ' + @($events).Count)
Write-Host 'Phase 8 read-only API OK.'
