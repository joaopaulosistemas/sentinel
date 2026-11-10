package com.sentinel.audit.resource;

import java.util.List;

/** Fatia de uma lista para paginação simples por {@code limit}/{@code offset}. */
final class Page {

    static final int MAX_LIMIT = 5000;

    private Page() {
    }

    /**
     * @param limit  máximo de itens; {@code null} ou negativo = sem limite (compatível com quem não pagina)
     * @param offset quantos itens pular; {@code null} ou negativo = 0
     */
    static <T> List<T> slice(List<T> all, Integer limit, Integer offset) {
        int from = Math.min(offset == null || offset < 0 ? 0 : offset, all.size());
        if (limit == null || limit < 0) {
            return from == 0 ? all : all.subList(from, all.size());
        }
        int to = (int) Math.min((long) from + Math.min(limit, MAX_LIMIT), all.size());
        return all.subList(from, to);
    }
}
