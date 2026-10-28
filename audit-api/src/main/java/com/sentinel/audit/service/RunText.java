package com.sentinel.audit.service;

import com.sentinel.audit.model.Finding;

/** Textos que vão para o banco ou para a tela: cortados ao tamanho da coluna e sem segredo nem stack trace. */
final class RunText {

    private static final String ELLIPSIS = "...";


    // Limites das colunas da entidade Finding
    private static final int MAX_TITLE        = 500;
    private static final int MAX_DESCRIPTION  = 2000;
    private static final int MAX_FILE_PATH    = 1000;
    private static final int MAX_SNIPPET      = 2000;
    private static final int MAX_RULE_ID      = 255;
    private static final int MAX_TOOL_SOURCE  = 100;
    private static final int MAX_CVE_ID       = 50;
    private static final int MAX_ERROR_MESSAGE = 1000;

    private RunText() {
    }

    /** Resume a causa raiz da exceção em uma frase curta, sem stack trace nem segredos. */
    static String describe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
        return root.getClass().getSimpleName() + ": " + message;
    }

    /** Mensagem de erro da auditoria, no tamanho da coluna. */
    static String error(String reason) {
        return truncate(reason, MAX_ERROR_MESSAGE);
    }

    /**
     * Garante que nenhum texto estoure o tamanho da coluna — um único valor longo
     * (ex.: mensagem do Semgrep) faria a transação inteira falhar.
     */
    static void fitColumns(Finding f) {
        f.setTitle(truncate(f.getTitle(), MAX_TITLE));
        f.setDescription(truncate(f.getDescription(), MAX_DESCRIPTION));
        f.setFilePath(truncate(f.getFilePath(), MAX_FILE_PATH));
        f.setCodeSnippet(truncate(f.getCodeSnippet(), MAX_SNIPPET));
        f.setRuleId(truncate(f.getRuleId(), MAX_RULE_ID));
        f.setToolSource(truncate(f.getToolSource(), MAX_TOOL_SOURCE));
        f.setCveId(truncate(f.getCveId(), MAX_CVE_ID));
    }

    static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
    }
}
