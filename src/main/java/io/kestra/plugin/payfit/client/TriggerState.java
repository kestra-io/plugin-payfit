package io.kestra.plugin.payfit.client;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.models.triggers.StatefulTriggerService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

/**
 * Detects whether a polling trigger has already stored a snapshot.
 * Entry TTL is applied when the snapshot is read. The KV document itself is not expired,
 * so an aged-out snapshot is not mistaken for the first run.
 * A stored value that cannot be parsed fails the poll instead of being treated as a first run.
 */
public final class TriggerState {
    private TriggerState() {
    }

    public static boolean initialized(RunContext runContext, String key) throws Exception {
        var kv = runContext.namespaceKv(runContext.flowInfo().namespace()).getValue(key);
        if (kv.isEmpty()) {
            return false;
        }
        // readState swallows parse errors and returns an empty map; fail here instead of firing every resource.
        JacksonMapper.ofJson().readValue(
            (byte[]) kv.get().value(),
            new TypeReference<List<StatefulTriggerService.Entry>>() {}
        );
        return true;
    }
}
