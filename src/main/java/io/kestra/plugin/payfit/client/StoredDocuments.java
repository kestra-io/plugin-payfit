package io.kestra.plugin.payfit.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

public final class StoredDocuments {
    private StoredDocuments() {
    }

    public static URI storeJson(RunContext runContext, Object value, String name) throws IOException {
        byte[] bytes = JacksonMapper.ofJson().writeValueAsBytes(value);
        return runContext.storage().putFile(new ByteArrayInputStream(bytes), name);
    }

    public static URI storeBytes(RunContext runContext, byte[] bytes, String name) throws IOException {
        return runContext.storage().putFile(new ByteArrayInputStream(bytes == null ? new byte[0] : bytes), name);
    }
}
