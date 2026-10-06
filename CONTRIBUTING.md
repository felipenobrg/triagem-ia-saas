# Contribuindo

Este repositório usa desenvolvimento orientado por especificações. A documentação define comportamento e critérios de aceite; o código deve materializar essas decisões e trazer evidência.

## Fluxo de mudança

1. Identifique a especificação e os requisitos afetados; se não houver, proponha uma nova em `specs/NNN-nome/`.
2. Descreva comportamento atual, mudança desejada, atores, cenários de sucesso, erros, autorização, dados e estados.
3. Revise ambiguidades e limites de escopo antes de planejar.
4. Atualize o plano técnico se a mudança alterar módulos, dependências, armazenamento, mensageria, IA ou operação.
5. Registre decisão significativa como ADR. Numere sequencialmente (`ADR-0001`, `ADR-0002`...).
6. Decomponha em tarefas independentes, cada uma com requisito relacionado e condição verificável de conclusão.
7. Implemente testes junto com o comportamento. Não declare requisito completo com teste pendente.
8. Abra PR com evidência, riscos e documentação atualizada.

## Estrutura da especificação

Uma especificação funcional deve conter:

- problema, objetivos e fora de escopo;
- atores e permissões;
- vocabulário do domínio;
- requisitos funcionais com IDs estáveis;
- regras de negócio e transições válidas;
- cenários de aceite no formato Dado/Quando/Então;
- falhas, reprocessamento e comportamento sem dependências externas;
- requisitos de segurança, privacidade e acessibilidade;
- requisitos de qualidade mensuráveis ou verificáveis;
- questões abertas, premissas e dependências.

Evite amarrar a especificação de comportamento a uma biblioteca ou classe. O plano registra como o sistema cumprirá o comportamento.

## Pronto para implementar

Uma especificação está pronta para virar plano quando cada requisito tiver ator/contexto, comportamento e resultado observável; estados e autorização estiverem definidos; erros relevantes forem cobertos; dependências e dúvidas bloqueadoras estiverem identificadas.

Uma tarefa está pronta para revisão quando a implementação e testes atenderem aos IDs indicados, o pipeline passar, migrações forem reversíveis ou tiverem procedimento seguro, documentação refletir o comportamento e não houver dados ou segredos reais.

## PR checklist

- [ ] Referencia `FR-*`, `NFR-*` ou `BR-*` e tarefas relacionadas.
- [ ] Atualiza a spec se comportamento ou aceite mudou.
- [ ] Inclui testes de sucesso, autorização e falhas aplicáveis.
- [ ] Verifica isolamento entre workspaces quando há dados multi-tenant.
- [ ] Cobre repetição/idempotência se há evento, fila ou chamada externa.
- [ ] Atualiza ADR, OpenAPI ou documentação operacional quando aplicável.
- [ ] Informa comandos executados e resultado, sem alegar verificações que não rodaram.
- [ ] Usa dados sintéticos; remove credenciais e conteúdo sensível de logs e fixtures.
- [ ] Commits seguem Conventional Commits; sem trailer de coautoria.

## Commits

Usar tipos como `feat`, `fix`, `docs`, `test`, `refactor`, `build`, `ci` e `chore`. Exemplos:

```text
docs: define request triage acceptance criteria
feat(triage): add human approval for AI suggestions
test(security): reject cross-workspace request access
```

Escrever o assunto em inglês, no imperativo, curto e sem ponto final. Usar `!` e seção `BREAKING CHANGE:` apenas quando houver incompatibilidade deliberada. Não adicionar trailers `Co-authored-by`.
