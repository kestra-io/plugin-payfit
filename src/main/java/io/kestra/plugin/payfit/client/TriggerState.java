package io.kestra.plugin.payfit.client;

import io.kestra.core.runners.RunContext;

/**
 * Detects whether a polling trigger has already stored a snapshot.
 * Entry TTL is applied when the snapshot is read. The KV document itself is not expired,
 * so an aged-out snapshot is not mistaken for the first run.
 */
public final class TriggerState {
    private TriggerState() {
    }

    public static boolean initialized(RunContext runContext, String key) throws Exception {
        return runContext.namespaceKv(runContext.flowInfo().namespace()).getValue(key).isPresent();
    }
}
