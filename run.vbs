Set objShell = CreateObject("WScript.Shell")
Set objFSO = CreateObject("Scripting.FileSystemObject")

projectDir = objFSO.GetParentFolderName(WScript.ScriptFullName)
cmdLine = "cmd.exe /c cd /d """ & projectDir & """ && .\gradlew :composeApp:run"

objShell.Run cmdLine, 1

WScript.Quit