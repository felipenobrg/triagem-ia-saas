# Constituição do projeto

Versão: 1.1.0 · Estado: princípios obrigatórios · Atualizada: 2026-10-06

Mudanças nestes princípios precisam de proposta, justificativa e decisão registrada. A constituição não substitui requisitos específicos da spec.

## 1. Spec antes de implementação

- Toda capacidade visível tem requisitos estáveis (`FR-*`, `BR-*`, `NFR-*`), cenários observáveis, tarefa e evidência.
- Mudança de escopo começa na spec; plano descreve abordagem e ADR registra trade-off duradouro.
- Ambiguidade que altera autorização, estados, dados ou falhas deve ser resolvida antes da implementação afetada.
- Código, teste, contrato e documentação devem concordar. Requisito planejado não é apresentado como entregue.

## 2. Domínio e arquitetura

- Use linguagem ubíqua, invariantes e limites explícitos. DDD é aplicado onde melhora entendimento e proteção de regras.
- Começar com monólito modular e dependências internas verificadas. Persistência, HTTP, broker, OIDC e SDK de IA ficam nas bordas.
- CQRS é leve: separar intenção de escrita e consulta sem exigir banco, serviço ou modelo eventual separado.
- Mensageria e eventos atendem consistência/desacoplamento observáveis. Não implicam event sourcing ou microserviços.
- RAG, MCP e cloud são extensões opcionais após o aceite do MVP, com spec e controles próprios.

## 3. Identidade, isolamento e privacidade

- Operação de negócio exige identidade autenticada e membership ativo no workspace; cliente nunca prova autorização por ID ou claim próprio.
- Isolamento deve ser coberto em rotas, consultas, relacionamentos de dados, jobs e logs.
- Aplicar menor privilégio, validação, limites de entrada/uso, gestão externa de segredos e auditoria sem corpo sensível.
- O projeto de mentoria usa dados sintéticos apenas. Sem conexão a ambiente, conta ou informação de trabalho dos participantes.
- Antes de qualquer piloto real, especificar retenção, exclusão, acesso, base operacional e segurança.

## 4. IA controlada

- IA retorna sugestão, nunca decisão final nem ação externa no MVP.
- Fake é padrão em desenvolvimento/testes. Provedor real exige opt-in, segredo externo, orçamento e telemetria.
- Validar schema, enumeração, limites e invariantes no servidor. Resposta estruturada do modelo não é fonte de confiança.
- Timeout ambíguo após envio não é retry seguro; persistir estado desconhecido e permitir decisão explícita.
- Registrar provider/model/schema/prompt version, status, tempo e uso sem registrar segredo, texto integral ou raciocínio privado.
- RAG é posterior e só recupera conteúdo autorizado, com filtro antes da busca e fontes verificáveis. MCP posterior, read-only inicial e autorizado em cada ferramenta.

## 5. Consistência e processamento assíncrono

- Persistir alteração local e intenção de publicar evento atomicamente via outbox quando ambos forem necessários.
- Mensagem é persistente e mínima; publisher confirma e trata mensagem não roteável; consumidor usa id estável e confirma depois da persistência.
- Assumir at-least-once. Idempotência mitiga redelivery, mas não torna efeito externo exatamente uma vez.
- Retry é limitado, atrasado e classificado; poison message vai à DLQ. Replay é explícito, autorizado e auditado.
- A fila não substitui o estado no banco. Falha de infraestrutura não pode apagar dados nem bloquear caminho manual definido pela spec.

## 6. Engenharia verificável e colaboração

- Build/CI cobrem testes de domínio, integração/contrato pertinentes, migrações e verificações de arquitetura.
- Critério de segurança/falha indica evidência. Pipeline verde por si só não prova qualidade ou operação.
- Ambiente local é reproduzível e instruções só são chamadas verificadas depois de outra pessoa executá-las.
- PR vincula requisitos/tarefas, evidencia comandos realmente executados e atualiza documentação.
- Alternar implementação e revisão entre participantes; descrever contribuição individual por evidência. Não usar trailer de coautoria.
- Commits seguem Conventional Commits.

## 7. Decisões e exceções

Registrar em ADR mudança de boundary, persistência, consistência, identidade, segurança, custo, IA, broker ou cloud. ADR traz contexto, decisão, alternativas, consequências, estado e gatilho de revisão. Exceção explicita motivo, escopo, risco, mitigação, responsável e revisão.
