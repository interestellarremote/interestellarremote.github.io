# Modelo de segurança

- Firebase não recebe a chave raiz. Ela é transportada no QR exibido localmente.
- Android Keystore envolve a chave no celular; DPAPI a protege no Windows.
- Regras de RTDB/Storage isolam contas e dispositivos, mas não substituem a criptografia ponta a ponta.
- O CLI é iniciado com o diretório de trabalho do projeto cadastrado,
  `--sandbox` e `--add-dir`. Somente o modo **Autônomo no projeto**, selecionado
  explicitamente pelo usuário, adiciona `--dangerously-skip-permissions`.
  Planejamento e somente leitura mantêm as permissões normais do CLI e evitam
  ferramentas que exigem confirmação no modo não interativo.
- O modo não interativo atual do CLI não expõe pedidos de aprovação para retransmissão. A ponte depende das proteções do próprio CLI e mantém deploy/Git push fora dos perfis de build cadastrados.
- Builds não aceitam comandos arbitrários do celular: somente IDs cadastrados localmente.
- Comandos expirados, repetidos ou fora de sequência são descartados.
- A navegação remota fica limitada às raízes autorizadas e aos projetos já cadastrados.
- `Permitir acesso total ao sistema de arquivos` nasce desligado e só pode ser alterado no painel local do PC. Mesmo ligado, o agente recebe apenas a pasta escolhida como projeto.

Metadados inevitavelmente visíveis ao Firebase: identificador aleatório do dispositivo, tipo do evento, horários, tamanhos aproximados e estado online. Nomes, prompts, respostas, comandos, logs e conteúdo de artefatos permanecem cifrados.

Antes de produção, habilite App Check no Android, alertas de orçamento, retenção curta no RTDB/Storage e revisão periódica das dependências.
