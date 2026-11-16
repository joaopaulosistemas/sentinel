package com.sentinel.audit.service;

import java.net.InetAddress;

/** Endereços que um destino de notificação (digitado pelo usuário) não pode alcançar: máquina local e redes internas. */
final class InternalAddresses {

    private static final int IPV4_BYTES = 4;
    private static final int BYTE_MASK = 0xff;
    private static final int CGNAT_MASK = 0xc0;
    private static final int CGNAT_PREFIX = 64;
    private static final int BENCHMARK_MASK = 0xfe;
    private static final int BENCHMARK_PREFIX = 18;
    private static final int IPV6_BYTES = 16;
    private static final int IPV6_ULA_MASK = 0xfe;
    private static final int IPV6_ULA_PREFIX = 0xfc;

    private InternalAddresses() { }

    static boolean isInternal(InetAddress a) {
        boolean local = a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress();
        return local || a.isSiteLocalAddress() || a.isMulticastAddress() || isUniqueLocalV6(a) || isReservedV4(a);
    }

    /**
     * Faixas IPv4 que o Java não chama de "site local" mas também não são a internet pública: 0.0.0.0/8, o
     * compartilhado de operadora (CGNAT, 100.64.0.0/10), 192.0.0.0/24 e a faixa de testes de rede 198.18.0.0/15.
     */
    private static boolean isReservedV4(InetAddress a) {
        byte[] b = a.getAddress();
        if (b.length != IPV4_BYTES) {
            return false;
        }
        int first = b[0] & BYTE_MASK;
        int second = b[1] & BYTE_MASK;
        int third = b[2] & BYTE_MASK;
        return first == 0 || isCgnat(first, second) || isIetfProtocol(first, second, third) || isBenchmark(first, second);
    }

    /** 100.64.0.0/10: espaço compartilhado de operadora. */
    private static boolean isCgnat(int first, int second) {
        return first == 100 && (second & CGNAT_MASK) == CGNAT_PREFIX;
    }

    /** 192.0.0.0/24. */
    private static boolean isIetfProtocol(int first, int second, int third) {
        return first == 192 && second == 0 && third == 0;
    }

    /** 198.18.0.0/15: faixa de testes de rede. */
    private static boolean isBenchmark(int first, int second) {
        return first == 198 && (second & BENCHMARK_MASK) == BENCHMARK_PREFIX;
    }

    private static boolean isUniqueLocalV6(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == IPV6_BYTES && (b[0] & IPV6_ULA_MASK) == IPV6_ULA_PREFIX;
    }
}
