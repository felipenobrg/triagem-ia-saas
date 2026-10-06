# Contribuição

Desenvolvimento guiado por specs; leia [AGENTS.md](AGENTS.md) e a [constituição](.specify/memory/constitution.md).

## Fluxo

1. Abra ou atualize spec em `specs/NNN-nome/` com problema, escopo, atores, permissões, estados, erros, requisitos e cenários Dado/Quando/Então.
2. Revise domínio e contrato com evidência. Não deixe dúvida sobre tenant, identidade, concorrência, idempotência ou recuperação escondida em comentário de código.
3. Atualize plano e ADR quando limites, dependências, dados, custo, segurança, mensageria ou operação mudarem.
4. Divida em tarefas pequenas relacionadas a IDs de requisito; uma tarefa deve ter resultado demonstrável.
5. Implemente testes adequados à regra e falha. Atualize spec, OpenAPI, runbook e tarefas conforme resultado.
6. Abra PR com requisitos, tarefas, evidências/comandos, limitações e autoria individual. Alterne driver/reviewer.

Use os modelos em [`specs/templates/`](specs/templates/) para novas propostas. Estado deve ser explícito: proposta, pronta para revisão, em andamento, aceita, adiada ou substituída. Só owner/participantes podem mover proposta para aceita; agente não presume aceite.

## Checklist de spec

- Problema, objetivo e fora de escopo definidos.
- Atores, identidade, autorização e tenancy definidos.
- Vocabulário, invariantes, estados e transições definidos.
- Caminhos de sucesso, validação, conflito, falha e recuperação observáveis.
- Contrato de API/evento e idempotência considerados.
- Segurança, privacidade, retenção e custos abordados.
- NFRs verificáveis; fontes externas citadas quando condicionam decisão.
- Premissas, questões pendentes e critérios de aceite identificados.

## Checklist de PR

- [ ] Relaciona `FR-*`, `BR-*`, `NFR-*` e IDs de tarefa.
- [ ] Testa regra, autorização e falha aplicáveis; informa apenas testes realmente executados.
- [ ] Inclui teste negativo de isolamento entre workspaces para recurso multi-tenant.
- [ ] Cobre repetição/concorrência quando há escrita, evento ou chamada externa.
- [ ] Atualiza OpenAPI, ADRs, documentação e tarefa se necessário.
- [ ] Usa dados sintéticos; não registra corpo livre ou segredo.
- [ ] Revisor independente verificou impacto e evidência.
- [ ] Commit usa Conventional Commits e não traz `Co-authored-by`.

## Commits

Assunto em inglês, curto, imperativo e sem ponto. Tipos comuns: `feat`, `fix`, `docs`, `test`, `refactor`, `build`, `ci`, `chore`.

```text
docs: define authenticated request intake
feat(triage): require human approval for suggestions
test(messaging): cover unroutable outbox events
```

Use `!`/`BREAKING CHANGE:` somente para incompatibilidade deliberada. Não incluir trailer de coautoria.
