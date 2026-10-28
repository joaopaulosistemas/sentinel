package com.sentinel.audit.analyzer.evidence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Leitor de CSV (RFC 4180) para a saída de {@code psql --csv} e {@code \copy ... csv header}: campos entre aspas,
 * aspas duplicadas e quebras de linha dentro de um campo (comuns no texto das consultas).
 */
final class Csv {

    /** Uma linha do arquivo, com os campos acessados pelo nome da coluna (minúsculo). */
    record Row(int line, Map<String, String> values) {
        String get(String name) {
            String v = values.get(name);
            return v == null ? "" : v;
        }

        /** Primeiro nome presente entre as alternativas (ex.: {@code total_exec_time} ou {@code total_time}). */
        String first(String... names) {
            for (String n : names) {
                String v = values.get(n);
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
            return "";
        }

        double number(String... names) {
            String v = first(names).strip();
            if (v.isEmpty()) {
                return 0;
            }
            try {
                return Double.parseDouble(v.replace(",", ""));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        boolean has(String name) {
            return values.containsKey(name);
        }
    }

    private Csv() {
    }

    /** Estado da leitura: registros fechados, o campo em montagem e a contagem de linhas do texto. */
    private static final class Reader {
        final List<List<String>> records = new ArrayList<>();
        final List<Integer> startLines = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        final StringBuilder cur = new StringBuilder();
        boolean quoted;
        boolean any;
        int line = 1;
        int recordStart = 1;

        boolean hasPending() {
            return any || cur.length() > 0 || !fields.isEmpty();
        }

        void closeRecord() {
            fields.add(cur.toString());
            records.add(fields);
            startLines.add(recordStart);
        }

        void endField() {
            fields.add(cur.toString());
            cur.setLength(0);
            any = true;
        }

        void endLine() {
            if (hasPending()) {
                closeRecord();
            }
            fields = new ArrayList<>();
            cur.setLength(0);
            any = false;
            line++;
            recordStart = line;
        }

        /** Dentro de aspas: devolve a posição do último caractere consumido. */
        int quotedChar(String text, int i) {
            char c = text.charAt(i);
            if (c == '"') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cur.append('"');
                    return i + 1;
                }
                quoted = false;
                return i;
            }
            if (c == '\n') {
                line++;
            }
            cur.append(c);
            return i;
        }

        /** Fora de aspas: devolve a posição do último caractere consumido. */
        int plainChar(String text, int i) {
            char c = text.charAt(i);
            if (c == '"' && cur.length() == 0) {
                quoted = true;
                any = true;
            } else if (c == ',') {
                endField();
            } else if (c == '\n' || c == '\r') {
                boolean crlf = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n';
                endLine();
                return crlf ? i + 1 : i;
            } else {
                cur.append(c);
                any = true;
            }
            return i;
        }
    }

    static List<Row> parse(String text) {
        Reader reader = new Reader();
        for (int i = 0; i < text.length(); i++) {
            i = reader.quoted ? reader.quotedChar(text, i) : reader.plainChar(text, i);
        }
        if (reader.hasPending()) {
            reader.closeRecord();
        }
        return toRows(reader.records, reader.startLines);
    }

    /** A primeira linha é o cabeçalho (minúsculo, sem BOM); as demais viram linhas com os campos pelo nome da coluna. */
    private static List<Row> toRows(List<List<String>> records, List<Integer> startLines) {
        List<Row> rows = new ArrayList<>();
        if (records.isEmpty()) {
            return rows;
        }
        List<String> header = new ArrayList<>();
        for (String h : records.get(0)) {
            header.add(h.strip().toLowerCase(Locale.ROOT).replace("﻿", ""));
        }
        for (int r = 1; r < records.size(); r++) {
            List<String> rec = records.get(r);
            Map<String, String> m = new HashMap<>();
            for (int k = 0; k < header.size() && k < rec.size(); k++) {
                m.put(header.get(k), rec.get(k));
            }
            rows.add(new Row(startLines.get(r), m));
        }
        return rows;
    }
}
