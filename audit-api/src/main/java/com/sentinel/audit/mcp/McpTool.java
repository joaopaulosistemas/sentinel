package com.sentinel.audit.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinel.audit.model.Category;
import com.sentinel.audit.model.Severity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Ferramentas que o Sentinel oferece por MCP. Nesta fase todas só leem: nenhuma inicia auditoria, suprime achado
 * ou muda regra. As que escrevem entram numa fase própria, desligadas por padrão e com confirmação.
 */
public enum McpTool {

    STATUS_AUDITORIA("status_auditoria",
            "Nota e situação da última auditoria concluída do sistema: nota geral e por dimensão, "
                    + "achados abertos por severidade, branch, commit e data."),
    BUSCAR_ACHADOS("buscar_achados",
            "Achados abertos da última auditoria, dos mais graves para os menos graves. "
                    + "O que aparece de cada achado depende do nível de dados definido para este sistema."),
    DIFF_AUDITORIA("diff_auditoria",
            "O que mudou desde a auditoria anterior: variação da nota, achados novos e achados resolvidos."),
    O_QUE_CORRIGIR_PRIMEIRO("o_que_corrigir_primeiro",
            "Problemas agrupados por regra, ordenados pelo quanto a nota sobe se forem corrigidos. "
                    + "O ganho vem do cálculo de nota do próprio Sentinel.");

    private final String wire;
    private final String description;

    McpTool(String wire, String description) {
        this.wire = wire;
        this.description = description;
    }

    /** Nome da ferramenta como o cliente MCP a enxerga. */
    public String wire() {
        return wire;
    }

    public String description() {
        return description;
    }

    /** Falso em todas as ferramentas desta fase. */
    public boolean writes() {
        return false;
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
            props.putObject("somenteNovos").put("type", "boolean")
                    .put("description", "Só os achados que não existiam na auditoria anterior");
            props.putObject("limite").put("type", "integer").put("minimum", 1).put("maximum", 50)
                    .put("description", "Quantos achados devolver (padrão 20)");
        }
        schema.putArray("required");
        return schema;
    }
}
