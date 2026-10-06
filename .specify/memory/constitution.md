# Constituição do projeto

Versão: 1.0.0
Estado: ratificada como princípios iniciais da especificação
Última atualização: 2026-10-06

Estes princípios são obrigatórios para especificações, planos, código, testes e revisões. Uma mudança de princípio exige proposta explícita, justificativa e aprovação registrada.

## 1. Especificação antes da implementação

- Toda capacidade visível ao usuário deve ter requisitos identificados, cenários de aceite observáveis e tarefas rastreáveis.
- Requisitos funcionais usam `FR-*`; requisitos de qualidade usam `NFR-*`; regras de domínio usam `BR-*`.
- Uma alteração de escopo começa pela atualização da especificação. Não substitua documentação por comportamento implícito no código.
- O PR demonstra a relação entre requisitos, implementação e testes. Requisito ainda não implementado deve continuar marcado como planejado.

## 2. Domínio e arquitetura modular

- Use linguagem ubíqua para as solicitações, espaços de trabalho, triagem e conhecimento da equipe.
- O backend começa como monólito modular com limites explícitos, dependências dirigidas para dentro e módulos testáveis sem subir a aplicação inteira.
- Regras e invariantes pertencem ao domínio/aplicação; detalhes de persistência, web, broker e provedor de IA ficam em adaptadores.
- Use CQRS de modo leve: comandos expressam intenções e protegem invariantes; consultas podem ter modelos de leitura próprios. Manter um deploy e um PostgreSQL até existir razão mensurável para separar.
- DDD, eventos, filas, RAG e MCP são ferramentas condicionadas a problemas definidos; não são metas de complexidade nem sinal automático de maturidade.

## 3. Segurança por workspace e privacidade

- Toda operação que lê ou altera recursos exige workspace autorizado no contexto autenticado. Nunca confiar em `workspaceId` informado pelo cliente como prova de acesso.
- Isolamento entre tenants é requisito de aceitação e precisa de testes negativos em APIs, consultas, busca vetorial, tarefas assíncronas, logs e ferramentas MCP.
- Aplicar menor privilégio, validação de entrada, limites de tamanho e de uso, gestão segura de segredos e trilha de auditoria sem registrar texto sensível por padrão.
- Armazenar apenas os dados necessários. Definir retenção, exclusão, exportação e tratamento de documentos antes de introduzir dados reais.
- A implantação de mentoria usa dados sintéticos. Sem integração com contas, ambientes ou dados de trabalho dos participantes.

## 4. IA assistiva, controlada e substituível

- IA produz sugestões, nunca decisão final ou ação operacional irreversível no escopo inicial.
- Toda sugestão deve apontar para a solicitação que a originou e guardar provedor/modelo, versão de prompt, horário, estado de validação e revisão humana, sem expor segredo.
- Validar respostas contra schema, limites e enumerações. Saída inválida, timeout ou indisponibilidade gera estado recuperável e caminho manual; não descartar nem autoaprovar a solicitação.
- O domínio depende de uma interface de triagem, não de SDK específico. Prompts e modelos são configuráveis e versionados.
- RAG recupera apenas conteúdo autorizado, aplica filtro de workspace antes da geração, registra referências usadas e não trata conteúdo recuperado como instrução confiável.
- MCP permanece extensão posterior e desligada por padrão. A primeira ferramenta deve ser de leitura, autenticada, autorizada por workspace, limitada e auditável; sem execução arbitrária de SQL ou ferramentas com escrita ampla.

## 5. Consistência e processamento assíncrono

- A atualização de estado local e o evento a publicar devem ser gravados atomicamente por outbox quando a operação requer ambos.
- Consumidores devem tolerar entrega repetida, detectar duplicação por identificador estável e só confirmar mensagem depois de persistir resultado ou uma falha recuperável.
- Retry é limitado e classifica erros transitórios e permanentes. Mensagens não processáveis terminam em DLQ com informação suficiente para investigação, sem vazar conteúdo sensível.
- A fila não é fonte de verdade do domínio. PostgreSQL mantém o estado da solicitação; reconciliação e replay precisam ser explícitos e seguros.
- Demonstrar cenários de publicação duplicada, consumidor reiniciado, timeout externo, DLQ e recuperação antes de apresentar fluxo assíncrono como confiável.

## 6. Engenharia verificável

- Automatizar build, testes unitários e de integração, análise estática e verificações de migração no CI.
- Testar invariantes de domínio sem infraestrutura; testar contratos HTTP/evento; usar testes de integração para PostgreSQL, RabbitMQ e migrações.
- Cada requisito de segurança, falha e qualidade tem evidência de teste ou inspeção indicada.
- Manter ambiente local reproduzível, logs estruturados, métricas úteis e instruções verificadas de operação. Não confundir “pipeline verde” com qualidade do produto.
- Commits seguem Conventional Commits. PRs e commits não recebem trailer `Co-authored-by` sem solicitação explícita.

## 7. Trade-offs registrados

- Decisões que afetam limites de módulos, persistência, consistência, segurança, custo, provedor de IA, mensageria ou cloud ganham uma ADR em `docs/architecture/decisions/`.
- Cada ADR descreve contexto, decisão, alternativas, consequências, status e quando reavaliar.
- Rever decisão diante de evidência nova; não manter padrão apenas por investimento já realizado.

## Exceções

Uma exceção exige motivo, escopo, risco, mitigação, responsável e data de revisão. A exceção é registrada na especificação ou em ADR e não apaga o princípio violado.
