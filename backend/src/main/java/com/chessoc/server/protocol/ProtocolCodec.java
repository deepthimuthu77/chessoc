package com.chessoc.server.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Encodes and decodes wire envelopes, enforcing protocol-version compatibility on decode. */
public final class ProtocolCodec {

    private final ObjectMapper mapper;
    private final ProtocolVersion supported;

    public ProtocolCodec() {
        this(ProtocolMapper.create(), ProtocolVersion.CURRENT);
    }

    public ProtocolCodec(ObjectMapper mapper, ProtocolVersion supported) {
        this.mapper = mapper;
        this.supported = supported;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public String encode(String type, Object payload) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode v = root.putArray("v");
        v.add(supported.major());
        v.add(supported.minor());
        root.put("type", type);
        root.set("payload", payload == null ? mapper.createObjectNode() : mapper.valueToTree(payload));
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new ProtocolException("failed to encode envelope", e);
        }
    }

    public Envelope decode(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ProtocolException("malformed envelope JSON", e);
        }
        JsonNode v = root.get("v");
        if (v == null || !v.isArray() || v.size() != 2) {
            throw new ProtocolException("missing or malformed protocol version");
        }
        ProtocolVersion version = new ProtocolVersion(v.get(0).asInt(), v.get(1).asInt());
        if (!supported.isCompatibleWith(version)) {
            throw new IncompatibleProtocolVersionException(supported, version);
        }
        JsonNode type = root.get("type");
        if (type == null || !type.isTextual()) {
            throw new ProtocolException("missing message type");
        }
        return new Envelope(version, type.asText(), root.get("payload"));
    }

    public <T> T payload(Envelope envelope, Class<T> type) {
        JsonNode node = envelope.payload() == null ? mapper.createObjectNode() : envelope.payload();
        try {
            return mapper.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new ProtocolException("failed to bind payload to " + type.getSimpleName(), e);
        }
    }
}
