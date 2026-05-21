param(
    [string]$ApkPath = "$env:USERPROFILE\Downloads\privatetalk-onesignal-debug.apk",
    [int]$Port = 8088
)

$ErrorActionPreference = "Stop"
$apk = Get-Item -LiteralPath $ApkPath
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Port)
$listener.Start()

while ($true) {
    $client = $listener.AcceptTcpClient()
    try {
        $stream = $client.GetStream()
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::ASCII, $false, 1024, $true)
        $requestLine = $reader.ReadLine()
        while (($line = $reader.ReadLine()) -ne $null -and $line -ne "") { }

        if ($requestLine -like "GET /privatetalk.apk *" -or $requestLine -like "GET / *") {
            $header = "HTTP/1.1 200 OK`r`nContent-Type: application/vnd.android.package-archive`r`nContent-Disposition: attachment; filename=`"privatetalk.apk`"`r`nContent-Length: $($apk.Length)`r`nConnection: close`r`n`r`n"
            $bytes = [System.Text.Encoding]::ASCII.GetBytes($header)
            $stream.Write($bytes, 0, $bytes.Length)
            $file = [System.IO.File]::OpenRead($apk.FullName)
            try {
                $file.CopyTo($stream)
            } finally {
                $file.Dispose()
            }
        } else {
            $body = [System.Text.Encoding]::UTF8.GetBytes("PrivateTalk APK server")
            $header = "HTTP/1.1 404 Not Found`r`nContent-Length: $($body.Length)`r`nConnection: close`r`n`r`n"
            $bytes = [System.Text.Encoding]::ASCII.GetBytes($header)
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Write($body, 0, $body.Length)
        }
    } catch {
    } finally {
        $client.Close()
    }
}
