package io.kestra.plugin.payfit.client;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.triggers.StatefulTriggerInterface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeSetTest {
    @Test
    void initialSnapshotRecordsWithoutFiring() {
        ChangeSet.Decision decision = ChangeSet.evaluate(
            List.of(Map.of("id", "a"), Map.of("id", "b")),
            Map.of(),
            StatefulTriggerInterface.On.CREATE,
            false,
            false,
            item -> item.get("id").toString(),
            item -> item.get("id").toString()
        );

        assertTrue(decision.initialSnapshot());
        assertTrue(decision.fired().isEmpty());
        assertEquals(2, decision.state().size());
    }

    @Test
    void initialSnapshotFiresWhenRequested() {
        ChangeSet.Decision decision = ChangeSet.evaluate(
            List.of(Map.of("id", "a")),
            Map.of(),
            StatefulTriggerInterface.On.CREATE,
            false,
            true,
            item -> item.get("id").toString(),
            item -> "v1"
        );

        assertEquals(1, decision.fired().size());
        assertEquals("a", decision.fired().getFirst().get("id"));
    }

    @Test
    void createFiresOnlyForNewIdsAndUpdateFiresOnVersionChange() {
        ChangeSet.Decision baseline = ChangeSet.evaluate(
            List.of(Map.of("id", "a", "status", "approved")),
            Map.of(),
            StatefulTriggerInterface.On.CREATE,
            false,
            false,
            item -> item.get("id").toString(),
            item -> item.get("status").toString()
        );

        ChangeSet.Decision created = ChangeSet.evaluate(
            List.of(Map.of("id", "a", "status", "approved"), Map.of("id", "b", "status", "approved")),
            baseline.state(),
            StatefulTriggerInterface.On.CREATE,
            true,
            false,
            item -> item.get("id").toString(),
            item -> item.get("status").toString()
        );
        assertEquals(List.of("b"), created.fired().stream().map(item -> item.get("id")).toList());

        ChangeSet.Decision updated = ChangeSet.evaluate(
            List.of(Map.of("id", "a", "status", "cancelled")),
            baseline.state(),
            StatefulTriggerInterface.On.UPDATE,
            true,
            false,
            item -> item.get("id").toString(),
            item -> item.get("status").toString()
        );
        assertEquals(1, updated.fired().size());

        ChangeSet.Decision unchanged = ChangeSet.evaluate(
            List.of(Map.of("id", "a", "status", "approved")),
            baseline.state(),
            StatefulTriggerInterface.On.CREATE_OR_UPDATE,
            true,
            false,
            item -> item.get("id").toString(),
            item -> item.get("status").toString()
        );
        assertTrue(unchanged.fired().isEmpty());
    }

    @Test
    void expiredSnapshotStillFires() {
        ChangeSet.Decision decision = ChangeSet.evaluate(
            List.of(Map.of("id", "a")),
            Map.of(),
            StatefulTriggerInterface.On.CREATE,
            true,
            false,
            item -> item.get("id").toString(),
            item -> "v1"
        );

        assertEquals(1, decision.fired().size());
        assertTrue(!decision.initialSnapshot());
    }

    @Test
    void missingIdFails() {
        assertThrows(PayfitException.class, () -> ChangeSet.evaluate(
            List.of(Map.of("name", "Ada")),
            Map.of(),
            StatefulTriggerInterface.On.CREATE,
            false,
            true,
            item -> null,
            item -> "v"
        ));
    }
}
