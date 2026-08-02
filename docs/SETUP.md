# Configuração do Antigravity Remote

## 1. Firebase

Este workspace já está provisionado no projeto `gen-lang-client-0120188776`
(nome exibido: **Antigravity Remote**), com Authentication/Google, Realtime
Database, Firestore, Storage e as cinco Cloud Functions publicados. O arquivo
`android/app/google-services.json` é local e permanece ignorado pelo Git.

Para recriar a configuração local da ponte a partir do arquivo Android, execute:

```powershell
.\scripts\configure-local-firebase.ps1
```

Isso grava `%LOCALAPPDATA%\AntigravityRemote\config.json`. A chave usada nesse
arquivo é a chave pública de cliente do Firebase; tokens da ponte continuam
protegidos pelo DPAPI.

Os passos abaixo servem para provisionar outro projeto, se necessário.

1. Crie um projeto no Firebase e ative o plano que permita Cloud Functions e Storage.
2. Em Authentication, habilite o provedor Google.
3. Crie Realtime Database, Firestore e Storage na mesma região sempre que possível.
4. Adicione um aplicativo Android com o pacote `com.antigravity.remote`, cadastre SHA-1/SHA-256 e baixe `google-services.json` para `android/app/`.
5. Configure o projeto padrão em `firebase/.firebaserc`.
6. Instale a CLI (`npm install -g firebase-tools`), autentique (`firebase login`) e execute:

```powershell
cd firebase/functions
npm install
npm run build
cd ..
firebase deploy --only functions,database,storage
```

Anote a URL do Realtime Database, o bucket, a região/URL base das Functions e a chave Web API pública exibida nas configurações do projeto.

## 2. Página oficial no GitHub Pages

O site público de distribuição do Bridge agora deve morar em `site/` e ser
publicado via GitHub Pages. O destino assumido neste workspace é:

```text
https://interestellarremote.github.io/
```

O conteúdo do site inclui:

- botão de download do instalador Windows;
- requisitos e passo a passo de pareamento;
- hash SHA-256 do instalador;
- aviso de segurança sobre o instalador não assinado;
- páginas de privacidade, segurança e termos de uso.

Se o repositório ou o usuário do GitHub mudarem, atualize as URLs em
`android/app/src/main/java/com/antigravity/remote/DownloadLinks.kt`.

## 3. Ponte Windows

Instale primeiro o Antigravity CLI oficial e execute `agy` uma vez para autenticar
com sua conta Google e autorizar o workspace. No Windows:

```powershell
irm https://antigravity.google/cli/install.ps1 | iex
agy
```

A ponte usa essa sessão protegida pelo Windows Credential Manager; não é necessária
uma `GEMINI_API_KEY`.

Copie `bridge/config.example.json` para `%LOCALAPPDATA%\AntigravityRemote\config.json` e preencha os quatro valores Firebase. Depois:

```powershell
cd bridge
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e .
.\.venv\Scripts\agy-remote.exe
```

O painel abrirá apenas em `http://127.0.0.1:8765`. Cadastre as pastas permitidas e perfis de build, gere o QR e escaneie no Android. A chave raiz e o refresh token ficam protegidos pelo DPAPI do usuário do Windows.

Na seção **Acesso ao sistema de arquivos**, adicione raízes como
`C:\Users\usuario\AndroidStudioProjects`. O celular poderá navegar e cadastrar
qualquer subpasta dessas raízes. A opção de acesso total permanece desligada por
padrão e só pode ser ativada nesse painel local.

Para empacotar, execute `bridge/scripts/build_windows.ps1`; opcionalmente compile `bridge/installer/AntigravityRemote.iss` com Inno Setup.

## 4. Aplicativo Android

Abra a pasta `android/` no Android Studio ou execute:

```powershell
cd android
.\gradlew.bat assembleDebug
```

Instale `android/app/build/outputs/apk/debug/app-debug.apk`, faça login com a conta Google autorizada e use “Escanear QR”. Sem `google-services.json`, a compilação funciona para testes locais, mas login e Firebase não funcionarão.

## 5. Uso

1. Deixe a ponte em execução e o computador conectado.
2. Selecione o computador e use o botão de pasta para navegar pelas raízes autorizadas ou escolha um projeto existente.
3. Crie uma conversa e envie um prompt. O modo `--print` do CLI 1.1.4 entrega a resposta ao final da execução.
4. Use Builds apenas para perfis cadastrados no Windows.
5. A escolha do usuário controla o Skip: somente **Autônomo no projeto** adiciona
   `--dangerously-skip-permissions`. **Planejamento** e **Somente leitura**
   preservam as permissões normais do CLI e usam apenas ferramentas de leitura
   não interativas, pois o modo `--print` não consegue responder a confirmações.
   Todos os modos permanecem limitados por `cwd`, `--sandbox` e `--add-dir`.
   Builds continuam limitados aos perfis cadastrados no Windows.
