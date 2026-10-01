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
        Object value = kv.get().value();
        if (!(value instanceof byte[] bytes)) {
            throw new PayfitException(unreadableSnapshot(key));
        }
        List<StatefulTriggerService.Entry> entries;
        try {
            entries = JacksonMapper.ofJson().readValue(
                bytes,
                new TypeReference<List<StatefulTriggerService.Entry>>() {}
            );
        } catch (Exception e) {
            throw new PayfitException(unreadableSnapshot(key), e);
        }
        if (entries == null) {
            throw new PayfitException(unreadableSnapshot(key));
        }
        return true;
    }

    private static String unreadableSnapshot(String key) {
        return "Could not read the PayFit trigger snapshot stored at '" + key + "'. Check this state key, then delete the KV entry so the next poll can record a new snapshot.";
    }
}
