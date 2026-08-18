package com.chessoc.server.match.state;

import com.chessoc.server.capability.Capability;
import com.chessoc.server.capability.CapabilityKind;
import com.chessoc.server.domain.Uci;
import com.chessoc.server.protocol.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Records everything the game sends to one subscriber. */
final class CapturingSink implements OutboundSink {

    record Msg(String type, Object payload) {
    }

    final List<Msg> messages = new ArrayList<>();

    @Override
    public void send(String type, Object payload) {
        messages.add(new Msg(type, payload));
    }

    Msg last() {
        return messages.get(messages.size() - 1);
    }

    List<Capability> latestCapabilities() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object payload = messages.get(i).payload();
            if (payload instanceof Messages.Snapshot s) {
                return s.capabilities();
            }
            if (payload instanceof Messages.ResyncOk r) {
                return r.capabilities();
            }
            if (payload instanceof Messages.Result res && res.capabilities().isPresent()) {
                return res.capabilities().get();
            }
        }
        return List.of();
    }

    Optional<Capability> moveCapability(Uci uci) {
        return latestCapabilities().stream()
                .filter(c -> c.kind() == CapabilityKind.MOVE && c.moveUci().equals(uci))
                .findFirst();
    }

    Optional<Capability> capability(CapabilityKind kind) {
        return latestCapabilities().stream().filter(c -> c.kind() == kind).findFirst();
    }
}
