# Interestellar Remote

Cliente Android e ponte Windows para acompanhar e operar o seu próprio workspace remotamente. O conteúdo é cifrado no dispositivo de origem; Firebase transporta somente envelopes cifrados e metadados mínimos.

## Componentes

- `android/`: aplicativo Kotlin/Jetpack Compose.
- `bridge/`: serviço Python 3.12 que controla o CLI do ambiente autenticado na máquina, builds e o painel local.
- `firebase/`: Cloud Functions, regras do Realtime Database e Storage.
- `contracts/`: esquema versionado dos comandos e eventos.
- `site/`: página oficial estática para GitHub Pages, com download do Bridge e páginas de política/segurança.

Consulte [SETUP.md](docs/SETUP.md) para configurar Firebase, iniciar a ponte, publicar a página oficial e compilar o APK.

## Funcionalidades da versão 0.3

- Caixa de tarefas persistente com estados, progresso, retomada após minimizar o app e repetição segura de falhas.
- Histórico local de conversas, rascunhos, comandos rápidos e detalhes técnicos opcionais.
- Seleção de modelo e modos autônomo, planejamento e somente leitura.
- Painel de projeto com arquivos autorizados, leitura de código, status Git e diff.
- Central de aprovações, builds, artefatos e registro de auditoria.
- Verificação de compatibilidade entre as versões do app, da ponte e do protocolo.
- Interface espacial escura com acentos neon, tipografia geométrica e estilos distintos para usuário e agente remoto.

## Segurança

- A chave raiz é transferida apenas no QR de pareamento e protegida por Android Keystore/Windows DPAPI.
- Cada conversa recebe uma chave derivada por HKDF-SHA256.
- Payloads usam AES-256-GCM com AAD contendo versão, dispositivo, conversa, sequência e tipo.
- A ponte aceita apenas projetos e perfis de build cadastrados localmente.
