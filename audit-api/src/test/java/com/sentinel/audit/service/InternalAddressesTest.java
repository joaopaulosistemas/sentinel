package com.sentinel.audit.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalAddressesTest {

    private static boolean internal(String ip) throws Exception {
        return InternalAddresses.isInternal(InetAddress.getByName(ip));
    }

    @Test
    void faixasPrivadasLocaisECompartilhadasDeOperadoraSaoInternas() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254",
                "0.0.0.0", "0.1.2.3", "100.64.0.1", "100.127.255.254", "192.0.0.8", "198.18.0.1", "198.19.255.255",
                "::1", "fd00::1", "fe80::1"}) {
            assertTrue(internal(ip), ip + " deveria ser tratado como interno");
        }
    }

    @Test
    void enderecosPublicosPassam() throws Exception {
        for (String ip : new String[]{"8.8.8.8", "1.1.1.1", "100.63.255.255", "100.128.0.1", "198.17.255.255", "198.20.0.1",
                "192.0.2.1", "2606:4700:4700::1111"}) {
            assertFalse(internal(ip), ip + " deveria ser público");
        }
    }
}
