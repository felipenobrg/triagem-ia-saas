# ADR-0004: AWS como sandbox temporário posterior

- **Estado:** opção futura, bloqueada até aceite local e revisão de custo
- **Data:** 2026-10-06

## Contexto

Mentoria quer praticar cloud, redes, secrets, observabilidade, custo e infraestrutura como código. Deploy público permanente não é necessário para aceitar MVP.

## Decisão

Depois da demo local, avaliar sandbox AWS efêmero e restrito, provisionado como código, somente com dados sintéticos. Antes de aplicar, confirmar região, serviço, preço/quota atuais, owner/conta, ingress, orçamento, alertas, secrets, backup/restore e teardown. Não é produção nem piloto com equipe real.

## Alternativas

- Cloud desde primeiro slice: mistura problemas de produto e plataforma e atrasa teste local.
- Deploy público permanente: custo e exposição desnecessários.
- Kubernetes: não há requisito que justifique cluster no exercício.
- Pular cloud: possível se tempo/custo não justificarem; trabalho local continua completo.

## Consequências

Terraform precisa revisão, destroy e verificação de recursos faturáveis residuais. Conta/produtos/preços mudam; verificar perto da execução. A autorização desta ADR é apenas para planejar, não para criar recursos.

## Reavaliar

Quando MVP local for aceito e houver janela, orçamento e responsável definidos. Exigir plano operacional e revisão antes de provisionar.
