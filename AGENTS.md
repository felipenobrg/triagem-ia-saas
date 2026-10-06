# Orientações para agentes e participantes

Antes de alterar comportamento, leia a constituição, spec ativa, plano e tarefas. Este repositório é spec-driven: implementação sem requisito rastreável não está pronta para começar.

## Regras do repositório

- Dados, realm, testes, demos e avaliações são sintéticos. Nunca usar dados, código, regras, exemplos ou credenciais da CWI, OSF ou clientes.
- Não alegar implementação, execução, segurança, custo, performance, adoção ou qualidade de IA sem evidência reproduzível.
- Toda mudança de comportamento atualiza spec, contrato, tarefas e docs relacionadas antes/de junto do código.
- Regras de negócio e invariantes pertencem ao domínio/aplicação. Controllers, ORM, RabbitMQ, Keycloak e SDKs de IA são adaptadores.
- Respeitar boundaries Spring Modulith. Não acessar tabela/internal de outro módulo por conveniência.
- Autorização sempre considera membership ativo em workspace. IDs/claims vindos do cliente não autorizam acesso.
- IA fake por padrão e em testes; chamada OpenAI só com opt-in explícito e cap configurado. Nunca pôr segredo no repo.
- Eventos são at-least-once e sem texto de solicitação. Nunca afirmar exactly-once. Efeitos externos de resultado ambíguo exigem estado explícito, não retry cego.
- RAG, MCP e cloud são extensões fora do MVP e necessitam spec/decisão próprias.
- Commits Conventional Commits em inglês. Não incluir trailer `Co-authored-by`.

## Pronto para revisar

PR informa requisitos e tarefas, comportamento implementado, testes/evidências realmente executados, decisões/alternativas, riscos e docs alteradas. Uma pessoa implementa e outra revisa; autoria/contribuição individual é descrita com precisão. Não marque tarefa/spec como aceita com teste ou operação pendente.
