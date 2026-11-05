# Como o Sentinel começou

## Primeira versão (Bobathon de Modernização Java)

Este projeto foi desenvolvido inteiramente com **IBM Bob como engenheiro de software** — não como assistente, mas como executor direto das tarefas.

### O fluxo de trabalho

```
Humano (Product Owner)          IBM Bob (Engenheiro)
─────────────────────           ────────────────────
Descreve o problema         →   Faz perguntas de refinamento
Responde às perguntas       →   Gera SENTINEL-AUDIT-TOOL.md (especificação)
Aprova a especificação      →   Implementa backend completo (43 arquivos Java)
                            →   Implementa frontend React + Carbon DS
                            →   Cria regras Semgrep YAML
                            →   Cria CLI Java para CI/CD
                            →   Cria Docker Compose
                            →   Valida build (mvn compile + 15 testes)
                            →   Corrige erros de compilação
                            →   Gera README e documentação
```

### Capacidades do Bob usadas

| Capacidade | Como foi usada |
|-----------|---------------|
| **Leitura de codebase** | Bob analisou o projeto legado Weather Demo para entender o contexto do negócio |
| **Geração de especificação** | Bob entrevistou o usuário e criou o documento técnico completo |
| **Implementação multi-arquivo** | Bob criou 50+ arquivos simultaneamente via subtasks paralelas |
| **Execução de comandos** | Bob rodou `mvn compile`, `mvn test`, corrigiu erros em tempo real |
| **Diagnóstico de ambiente** | Bob identificou conflito Java 8 vs Java 21, Maven 3.0.5 vs 3.9.x e resolveu |
| **Raciocínio técnico** | Bob escolheu Quarkus, Carbon DS, Panache baseado no contexto do negócio |

### O que o humano fez

1. Descreveu o problema em linguagem natural
2. Respondeu 4 perguntas de refinamento
3. Digitou `implementar`

**Tempo total:** ~45 minutos de conversa  
**Linhas de código geradas:** ~3.000 linhas Java + ~1.500 linhas TypeScript/React + ~300 linhas YAML

---

## A Equipe

Este projeto foi construído durante o **Bobathon de Modernização Java** — um workshop da área de tecnologia.

| Papel | Nome | Contribuição |
|-------|------|-------------|
| **Product Owner / Idealizador** | Participante Bobathon | Definição do problema, requisitos, validação |
| **Engenheiro de Software** | IBM Bob | Especificação, implementação, testes, documentação |
| **Revisão Técnica** | Time da organização | Validação dos padrões e contexto do negócio |

> 💡 **Este é exatamente o propósito do Bobathon:** demonstrar que com IBM Bob, um profissional de negócio com conhecimento técnico básico pode construir uma solução de software completa e funcional em horas, não semanas.

---
