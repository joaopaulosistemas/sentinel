package com.sentinel.audit.pack;

import java.util.List;

/** Pacote inválido; a mensagem lista <b>todos</b> os problemas encontrados, não só o primeiro. */
public class PackException extends RuntimeException {

    private final List<String> problems;

    public PackException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
