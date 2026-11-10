package com.sentinel.audit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AnalyzerRunningTest {

    @Test
    void cancelarInterrompeAThreadQueExecutaOAnalisador() throws Exception {
        AtomicReference<Thread> holder = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            holder.set(Thread.currentThread());
            started.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            finished.countDown();
        });
        worker.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));

        AnalyzerRunner.Running running = new AnalyzerRunner.Running(new CompletableFuture<>(), holder);
        running.cancel();

        assertTrue(finished.await(5, TimeUnit.SECONDS), "a thread deveria ter acordado");
        assertTrue(interrupted.get());
        assertTrue(running.future().isCancelled());
    }

    @Test
    void cancelarSemThreadAtivaSoCancelaOFuturo() {
        AnalyzerRunner.Running running = new AnalyzerRunner.Running(new CompletableFuture<>(), new AtomicReference<>());
        running.cancel();
        assertTrue(running.future().isCancelled());
    }
}
