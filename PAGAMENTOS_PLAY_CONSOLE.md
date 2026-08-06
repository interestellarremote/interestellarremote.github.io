# Pagamentos do Interestellar Remote

## Produtos

Cadastre uma assinatura no Google Play Console usando este ID de produto:

- `interstellar_remote_pro`

Dentro dela, cadastre estes planos básicos:

- `monthly` — R$ 19,90 por mês
- `yearly` — R$ 159,90 por ano

Se quiser teste grátis, ele deve ser configurado como oferta dentro do plano básico compatível. O teste é controlado pelo Google Play, então o app não usa um contador local que poderia ser alterado.

## Fluxo já preparado no aplicativo

- Google Play Billing Library 9.1.0
- Consulta dos produtos e preços localizados pelo Google Play
- Seleção automática da oferta de 7 dias quando ela estiver disponível para o usuário
- Compra mensal e anual
- Recuperação da assinatura ao abrir o app novamente
- Tratamento de compra pendente
- Reconhecimento da compra dentro do prazo do Google Play
- Botão para gerenciar/cancelar a assinatura no Google Play
- Tela premium dedicada com comparação mensal/anual e destaque da economia anual
- Acesso aos planos pela tela principal, barra superior e configurações

## Antes de publicar

1. Criar os produtos e as ofertas no Play Console.
2. Publicar o app em uma faixa de teste interno/fechado.
3. Adicionar os e-mails de teste como testadores de licença.
4. Instalar o app pela Play Store; um APK instalado diretamente não recebe os produtos reais da Play.
5. Implementar a validação do `purchaseToken` no backend antes de liberar funções pagas em produção. O código Android deixou esse ponto marcado em `BillingManager.kt`; não é seguro confiar somente no estado local do aparelho.

Os preços mostrados no app são apenas um fallback visual até o Google Play devolver os valores cadastrados. Na compra, o diálogo oficial do Google Play é a fonte final do preço e das condições.
