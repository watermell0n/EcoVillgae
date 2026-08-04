package com.b201.ecovillage.realtime.application;

import java.util.Optional;

public interface RealtimeTicketStore {

    IssuedRealtimeTicket issue(RealtimeTicket ticket);

    Optional<RealtimeTicket> consume(String rawTicket);
}
