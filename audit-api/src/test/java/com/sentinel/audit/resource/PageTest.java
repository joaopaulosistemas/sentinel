package com.sentinel.audit.resource;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageTest {

    private static final List<Integer> L = List.of(1, 2, 3, 4, 5);

    @Test
    void semParametrosDevolveTudo() {
        assertEquals(L, Page.slice(L, null, null));
        assertEquals(L, Page.slice(L, -1, -5));
    }

    @Test
    void limitEOffset() {
        assertEquals(List.of(1, 2), Page.slice(L, 2, null));
        assertEquals(List.of(3, 4), Page.slice(L, 2, 2));
        assertEquals(List.of(5), Page.slice(L, 10, 4));
    }

    @Test
    void offsetAlemDoFimDevolveVazio() {
        assertEquals(List.of(), Page.slice(L, 3, 9));
        assertEquals(List.of(), Page.slice(L, null, 9));
    }

    @Test
    void limitZeroDevolveVazio() {
        assertEquals(List.of(), Page.slice(L, 0, 0));
    }
}
