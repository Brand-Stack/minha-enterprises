$authBody = '{"email":"admin","password":"Minha@123"}'
$login = Invoke-RestMethod -Uri 'http://103.235.106.107:1104/api/auth/login' -Method Post -ContentType 'application/json' -Body $authBody
$token = $login.accessToken
Write-Host "Token obtained successfully."

$headers = @{ "Authorization" = "Bearer $token" }

# 1. Check mapped devices
try {
    $devices = Invoke-RestMethod -Uri 'http://103.235.106.107:1104/api/biometric-devices' -Method Get -Headers $headers
    Write-Host "Devices count: $($devices.Count)"
} catch {
    Write-Host "Devices error: $_"
}

# 2. Check mappings endpoint (if updated backend is live or old)
try {
    $mappings = Invoke-RestMethod -Uri 'http://103.235.106.107:1104/api/biometric-devices/mappings' -Method Get -Headers $headers
    Write-Host "Mappings count: $($mappings.Count)"
    $mappings | ConvertTo-Json -Depth 3 | Write-Host
} catch {
    Write-Host "Mappings endpoint: $_"
}

# 3. Check attendance for today
$today = (Get-Date).ToString('yyyy-MM-dd')
try {
    $att = Invoke-RestMethod -Uri "http://103.235.106.107:1104/api/attendance/daily?date=$today" -Method Get -Headers $headers
    Write-Host "Attendance records for $today count: $($att.Count)"
    $att | ConvertTo-Json -Depth 4 | Write-Host
} catch {
    Write-Host "Attendance error: $_"
}
