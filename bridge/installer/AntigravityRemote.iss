#define MyAppName "Interestellar Remote Bridge"
#define MyAppVersion "0.2.8"
#define MyAppExeName "AntigravityRemote.exe"

[Setup]
AppId={{B45D4AFE-B6F5-43D8-A46D-246CF5157838}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
DefaultDirName={localappdata}\Programs\Antigravity Remote
PrivilegesRequired=lowest
OutputBaseFilename=InterestellarRemoteSetup-0.2.8
Compression=lzma2
SolidCompression=yes

[Tasks]
Name: "startup"; Description: "Iniciar com o Windows"; Flags: unchecked
Name: "desktopicon"; Description: "Criar atalho na Área de Trabalho"; GroupDescription: "Atalhos adicionais:"; Flags: unchecked

[Files]
Source: "..\dist-publish\AntigravityRemote\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{commondesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: startup

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Abrir {#MyAppName}"; Flags: nowait postinstall skipifsilent
