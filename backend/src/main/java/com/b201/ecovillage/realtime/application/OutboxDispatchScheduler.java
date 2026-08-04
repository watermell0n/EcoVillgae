package com.b201.ecovillage.realtime.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxDispatchScheduler {

    private final OutboxDispatchWorker worker;

    public OutboxDispatchScheduler(OutboxDispatchWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${ecovillage.realtime.dispatch-delay-ms:250}")
    public void dispatch() {
        worker.dispatchPending();
    }
}
