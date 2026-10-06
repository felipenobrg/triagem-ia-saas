# ADR-0004: AWS apenas como sandbox temporário de aprendizagem

- **Status:** Accepted with implementation gate
- **Date:** 2026-10-06

## Context

Cloud, containers, redes, observabilidade, secrets, custo e infraestrutura como código fazem parte dos objetivos formativos. Uma implantação pública permanente traz custo e risco que não são necessários para demonstrar esses conceitos.

## Decision

Planejar um ambiente AWS isolado, efêmero e provisionado por Terraform após o fluxo local estar verificável. Usar somente dados sintéticos, acesso restrito, orçamento revisado, alertas e procedimento de teardown testado. A implementação exige verificar região, disponibilidade, quotas, preço e autoridade da conta imediatamente antes da provisionar. Não é produção nem piloto com usuários reais.

## Alternatives considered

- **Deploy público permanente:** rejeitado por custo/risco recorrente fora da necessidade de mentoria.
- **Pular cloud:** rejeitado porque reduz evidência de deploy e operação em ambiente real.
- **Cluster Kubernetes:** não selecionado para primeira experiência; mais componentes sem requisito para o produto.

## Consequences

- Terraform deve ser revisado, destruir os recursos e checar resíduos billable.
- Secrets e dados de demonstração seguem política própria; nenhum dado real de CWI/OSF/cliente.
- Preços, produtos suportados e configurações cloud são variáveis e precisam ser confirmados perto da execução.

## Revisit when

Surja necessidade explícita de piloto. Exigir responsável, dados permitidos, base de segurança/privacidade, observabilidade, backup/restore, custo aprovado e autorização separada antes de qualquer exposição real.
