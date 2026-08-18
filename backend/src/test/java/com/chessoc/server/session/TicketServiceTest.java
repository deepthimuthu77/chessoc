package com.chessoc.server.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TicketServiceTest {

    @Test
    void consumesOnce() {
        AtomicLong now = new AtomicLong(0);
        TicketService tickets = new TicketService(now::get, 1_000);
        String ticket = tickets.issue("session-1");

        assertThat(tickets.consume(ticket)).isEqualTo("session-1");
        assertThatThrownBy(() -> tickets.consume(ticket)).isInstanceOf(AuthException.class);
    }

    @Test
    void rejectsExpiredTicket() {
        AtomicLong now = new AtomicLong(0);
        TicketService tickets = new TicketService(now::get, 1_000);
        String ticket = tickets.issue("session-1");
        now.set(1_000);
        assertThatThrownBy(() -> tickets.consume(ticket)).isInstanceOf(AuthException.class);
    }
}
