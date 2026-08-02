# Arquitetura e protocolo

```text
Android (Firebase Auth + Keystore)
        │  comandos/eventos AES-GCM
        ▼
Firebase RTDB ── Cloud Functions ── FCM
        ▲               │
        │               └─ pareamento/revogação
Windows Bridge (DPAPI + SQLite + Antigravity CLI)
        │
        ├─ projetos locais autorizados
        ├─ perfis de build confinados
        └─ artefatos cifrados → Firebase Storage
```

O envelope público está em `contracts/protocol-v1.json`. O payload é JSON cifrado com uma chave derivada por conversa. O AAD vincula versão, dispositivo, conversa, sequência, tipo e versão da chave, impedindo que a nuvem mova uma mensagem para outro contexto.

O RTDB mantém caixas por dispositivo. O Android só escreve comandos para dispositivos pertencentes à sua conta; a ponte só lê sua própria caixa e publica eventos. IDs processados e sequências são persistidos no SQLite para impedir reexecução após reconexões.

Artefatos usam o cabeçalho `AGYR1`, nonce de 12 bytes e AES-256-GCM. O nome remoto faz parte do AAD. O app descriptografa somente depois de o usuário escolher um destino pelo Storage Access Framework.
