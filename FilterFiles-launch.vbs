Set WshShell = CreateObject("WScript.Shell")
WshShell.Run Chr(34) & "FilterFiles-launch.bat" & Chr(34), 0
Set WshShell = Nothing