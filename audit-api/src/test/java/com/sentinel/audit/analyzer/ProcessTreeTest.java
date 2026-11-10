package com.sentinel.audit.analyzer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
class ProcessTreeTest {

    @Test
    void encerraOProcessoETodosOsDescendentes() throws Exception {
        // o shell inicia um "sleep" filho e espera por ele: matar só o shell deixaria o sleep vivo
        Process shell = new ProcessBuilder("sh", "-c", "sleep 60 & wait").start();
        awaitDescendant(shell);
        List<ProcessHandle> children = shell.descendants().toList();
        assertFalse(children.isEmpty(), "o processo filho deveria existir antes do encerramento");

        ProcessTree.destroy(shell);

        assertTrue(shell.waitFor(5, TimeUnit.SECONDS), "o shell deveria ter terminado");
        for (ProcessHandle child : children) {
            assertTrue(awaitDead(child), "o descendente deveria ter terminado");
        }
    }

    @Test
    void processoJaEncerradoNaoLanca() throws Exception {
        Process done = new ProcessBuilder("true").start();
        done.waitFor();
        ProcessTree.destroy(done);
        assertFalse(done.isAlive());
    }

    /** Morto ou zumbi: em contêineres sem init ninguém recolhe o filho órfão, que fica "Z" mas já não executa nada. */
    private static boolean awaitDead(ProcessHandle p) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (!p.isAlive() || isZombie(p.pid())) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static boolean isZombie(long pid) {
        try {
            String stat = java.nio.file.Files.readString(java.nio.file.Path.of("/proc/" + pid + "/stat"));
            return stat.substring(stat.lastIndexOf(')') + 2).startsWith("Z");
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static void awaitDescendant(Process p) throws InterruptedException {
        for (int i = 0; i < 100 && p.descendants().findAny().isEmpty(); i++) {
            Thread.sleep(50);
        }
    }
}
