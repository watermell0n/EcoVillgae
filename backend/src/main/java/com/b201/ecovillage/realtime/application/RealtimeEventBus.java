package com.b201.ecovillage.realtime.application;

public interface RealtimeEventBus {

    void publish(RealtimeEventEnvelope event);
}
