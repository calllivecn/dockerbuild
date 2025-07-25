# 优先获取永久（Permanent）IPv6地址
# 然后获取（MANAGETEMPADDR）IPv6地址
# 然后获取（IFA_F_TEMPORARY）IPv6地址
# 最后，没有ipv6就在标准输出空字符串。
# 需要排除::1。

# 1. Permanent 地址（WellKnown 前缀和后缀）
$permanent = Get-NetIPAddress -AddressFamily IPv6 `
    | Where-Object {
        $_.PrefixOrigin -eq 'WellKnown' -and
        $_.SuffixOrigin -eq 'WellKnown' -and
        $_.AddressState -eq 'Preferred' -and
        $_.IPAddress -ne '::1'
    } `
    | Select-Object -ExpandProperty IPAddress -First 1

if ($permanent) {
    Write-Output $permanent
    exit 0
}

# 2. MANAGETEMPADDR 地址（WellKnown 前缀，Random 后缀）
$managedTemp = Get-NetIPAddress -AddressFamily IPv6 `
    | Where-Object {
        $_.PrefixOrigin -eq 'WellKnown' -and
        $_.SuffixOrigin -eq 'Random' -and
        $_.AddressState -eq 'Preferred' -and
        $_.IPAddress -ne '::1'
    } `
    | Select-Object -ExpandProperty IPAddress -First 1

if ($managedTemp) {
    Write-Output $managedTemp
    exit 0
}

# 3. TEMPORARY 地址（Temporary 前缀，Random 后缀）
$temp = Get-NetIPAddress -AddressFamily IPv6 `
    | Where-Object {
        $_.PrefixOrigin -eq 'Temporary' -and
        $_.SuffixOrigin -eq 'Random' -and
        $_.AddressState -eq 'Preferred' -and
        $_.IPAddress -ne '::1'
    } `
    | Select-Object -ExpandProperty IPAddress -First 1

if ($temp) {
    Write-Output $temp
    exit 0
}

# 4. 没有可用 IPv6 地址，输出空字符串
Write-Output ""