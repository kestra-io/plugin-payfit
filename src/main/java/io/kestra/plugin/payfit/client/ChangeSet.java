package io.kestra.plugin.payfit.client;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.kestra.core.models.triggers.StatefulTriggerInterface;
import io.kestra.core.models.triggers.StatefulTriggerService;

public final class ChangeSet {
    private ChangeSet() {
    }

    public record Decision(List<Map<String, Object>> fired, Map<String, StatefulTriggerService.Entry> state, boolean initialSnapshot) {
    }

    public static Decision evaluate(
        List<Map<String, Object>> items,
        Map<String, StatefulTriggerService.Entry> previous,
        StatefulTriggerInterface.On on,
        boolean initialized,
        boolean fireOnInitial,
        Function<Map<String, Object>, String> idFunction,
        Function<Map<String, Object>, String> versionFunction
    ) {
        Map<String, StatefulTriggerService.Entry> state = new LinkedHashMap<>(previous == null ? Map.of() : previous);
        List<Map<String, Object>> ordered = items == null ? List.of() : items;
        Instant now = Instant.now();

        if (!initialized && !fireOnInitial) {
            for (Map<String, Object> item : ordered) {
                String id = requiredId(item, idFunction);
                state.put(id, new StatefulTriggerService.Entry(id, versionFunction.apply(item), now, now));
            }
            return new Decision(List.of(), state, true);
        }

        List<Map<String, Object>> fired = new ArrayList<>();
        for (Map<String, Object> item : ordered) {
            String id = requiredId(item, idFunction);
            StatefulTriggerService.Entry candidate = StatefulTriggerService.Entry.candidate(id, versionFunction.apply(item), now);
            StatefulTriggerService.StateUpdate update = StatefulTriggerService.computeAndUpdateState(state, candidate, on);
            if (update.fire()) {
                fired.add(item);
            }
        }
        return new Decision(List.copyOf(fired), state, false);
    }

    private static String requiredId(Map<String, Object> item, Function<Map<String, Object>, String> idFunction) {
        String id = item == null ? null : idFunction.apply(item);
        if (id == null || id.isBlank()) {
            throw new PayfitException("PayFit resource is missing an id and cannot be tracked by the trigger");
        }
        return id;
    }
}
