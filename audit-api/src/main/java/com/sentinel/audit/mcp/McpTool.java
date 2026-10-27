package com.sentinel.audit.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Confidence;
import com.sentinel.audit.model.Severity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Ferramentas que o Sentinel oferece por MCP. As de leitura vêm ligadas; as que gravam ({@link #writes()}) ficam
 * desligadas até a pessoa liberá-las, e só escrevem na área Revisão do Claude (sugestões e regras propostas).
 * Nenhuma inicia auditoria, suprime achado ou muda a nota.
 */
public enum McpTool {

    STATUS_AUDITORIA("status_auditoria",
            "Nota e situação da última auditoria concluída do sistema: nota geral e por dimensão, "
                    + "achados abertos por severidade, branch, commit e data."),
    BUSCAR_ACHADOS("buscar_achados",
            "Achados abertos da última auditoria, dos mais graves para os menos graves, cada um com a confiança do Sentinel "
                    + "(HIGH, MEDIUM ou LOW) e o motivo. O que aparece de cada achado depende do nível de dados definido para este sistema."),
    DIFF_AUDITORIA("diff_auditoria",
            "O que mudou desde a auditoria anterior: variação da nota, achados novos e achados resolvidos."),
    O_QUE_CORRIGIR_PRIMEIRO("o_que_corrigir_primeiro",
            "Problemas agrupados por regra, ordenados pelo quanto a nota sobe se forem corrigidos. "
                    + "O ganho vem do cálculo de nota do próprio Sentinel."),
    REGISTRAR_SUGESTAO("registrar_sugestao",
            "Registra uma sugestão sua sobre o sistema na área Revisão do Claude. Não cria achado e não muda a nota; "
                    + "uma pessoa decide o que fazer com ela.", true),
    PROPOR_REGRA("propor_regra",
            "Propõe uma regra Semgrep para este sistema. Ela entra no funil (revisão por uma pessoa, teste de amostra, "
                    + "observação fora da nota) e só pesa na nota se for promovida por uma pessoa.", true),
    INICIAR_AUDITORIA("iniciar_auditoria",
            "Pede uma nova auditoria do sistema. Nada roda na hora: o pedido fica esperando uma pessoa aprovar no dashboard "
                    + "(vale 1 hora). Use status_pedidos para saber a decisão.", true),
    SUPRIMIR_ACHADO("suprimir_achado",
            "Pede para suprimir um achado da última auditoria como falso positivo (só esse motivo). Nada muda na hora: "
                    + "uma pessoa aprova no dashboard e a supressão vale por 90 dias. Use status_pedidos para saber a decisão.", true),
    STATUS_PEDIDOS("status_pedidos",
            "Situação dos pedidos recentes feitos por você (esperando, aprovado, recusado, vencido).");

    private final String wire;
    private final String description;
    private final boolean writes;

    McpTool(String wire, String description) {
        this(wire, description, false);
    }

    McpTool(String wire, String description, boolean writes) {
        this.wire = wire;
        this.description = description;
        this.writes = writes;
    }

    /** Nome da ferramenta como o cliente MCP a enxerga. */
    public String wire() {
        return wire;
    }

    public String description() {
        return description;
    }

    /** Verdadeiro nas que gravam, e só na área Revisão do Claude: nenhuma toca em achado, regra do motor ou nota. */
    public boolean writes() {
        return writes;
    }

    public static Optional<McpTool> byWire(String name) {
        return Arrays.stream(values()).filter(t -> t.wire.equals(name)).findFirst();
    }

    /** Esquema JSON dos argumentos. */
    public ObjectNode inputSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        if (this == BUSCAR_ACHADOS) {
            ArrayNode sev = props.putObject("severidade").put("type", "string")
                    .put("description", "Só esta severidade").putArray("enum");
            Arrays.stream(Severity.values()).forEach(s -> sev.add(s.name()));
            ArrayNode cat = props.putObject("categoria").put("type", "string")
                    .put("description", "Só esta categoria").putArray("enum");
            Arrays.stream(Category.values()).forEach(c -> cat.add(c.name()));
            ArrayNode conf = props.putObject("confianca").put("type", "string")
                    .put("description", "Só esta confiança do Sentinel: LOW costuma ser barulho (teste, exemplo, regra recusada); "
                            + "HIGH é confirmado por duas ferramentas ou é vulnerabilidade conhecida").putArray("enum");
            Arrays.stream(Confidence.values()).forEach(c -> conf.add(c.name()));
            props.putObject("somenteNovos").put("type", "boolean")
                    .put("description", "Só os achados que não existiam na auditoria anterior");
            props.putObject("limite").put("type", "integer").put("minimum", 1).put("maximum", 50)
                    .put("description", "Quantos achados devolver (padrão 20)");
        }
        if (this == INICIAR_AUDITORIA) {
            props.putObject("branch").put("type", "string").put("description", "Branch a auditar (padrão: a do agendamento do sistema)");
            schema.putArray("required");
            return schema;
        }
        if (this == SUPRIMIR_ACHADO) {
            props.putObject("achadoId").put("type", "integer").put("description", "Id do achado (vem de buscar_achados)");
            props.putObject("motivo").put("type", "string").putArray("enum").add("FALSE_POSITIVE");
            props.putObject("justificativa").put("type", "string").put("minLength", 10).put("maxLength", 1000)
                    .put("description", "Por que é falso positivo");
            schema.putArray("required").add("achadoId").add("motivo").add("justificativa");
            return schema;
        }
        if (this == REGISTRAR_SUGESTAO) {
            props.putObject("titulo").put("type", "string").put("maxLength", 200).put("description", "Resumo da sugestão");
            props.putObject("detalhe").put("type", "string").put("maxLength", 2000).put("description", "Explicação e como corrigir");
            props.putObject("regra").put("type", "string").put("description", "Id da regra relacionada, se houver");
            props.putObject("arquivo").put("type", "string").put("description", "Arquivo relacionado, se houver");
            props.putObject("linha").put("type", "integer").put("minimum", 1).put("description", "Linha, se houver");
            schema.putArray("required").add("titulo");
            return schema;
        }
        if (this == PROPOR_REGRA) {
            props.putObject("id").put("type", "string").put("pattern", "[a-z0-9][a-z0-9-]{2,59}")
                    .put("description", "Id curto da regra (minúsculas, números e hífen)");
            props.putObject("titulo").put("type", "string").put("maxLength", 200);
            props.putObject("motivo").put("type", "string").put("maxLength", 2000)
                    .put("description", "Por que a regra vale: o que ela pega e por que se repete");
            props.putObject("regraSemgrep").put("type", "string").put("maxLength", 8000)
                    .put("description", "YAML Semgrep com uma única regra, de id 'local-<id>'");
            schema.putArray("required").add("id").add("titulo").add("regraSemgrep");
            return schema;
        }
        schema.putArray("required");
        return schema;
    }
}
