package io.kestra.plugin.payfit.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;

/**
 * Applies Kestra {@link FetchType} to a paginated PayFit collection.
 */
public final class ListFetch {
    private ListFetch() {
    }

    public record Plan(boolean fetchAll, Integer pageSize) {
    }

    public record Result<T>(int count, URI uri, T one, List<T> many) {
    }

    public static Plan plan(FetchType fetchType, Integer requestedPageSize) {
        if (fetchType == FetchType.FETCH_ONE) {
            return new Plan(false, 1);
        }
        return new Plan(true, requestedPageSize);
    }

    public static <T> Result<T> shape(RunContext runContext, FetchType fetchType, List<T> items, String ionFileName) throws IOException {
        List<T> rows = items == null ? List.of() : items;
        return switch (fetchType) {
            case NONE -> new Result(rows.size(), null, null, null);
            case FETCH_ONE -> rows.isEmpty()
                ? new Result(0, null, null, null)
                : new Result(1, null, rows.getFirst(), null);
            case STORE -> new Result(rows.size(), storeIon(runContext, rows, ionFileName), null, null);
            case FETCH -> new Result(rows.size(), null, null, rows);
        };
    }

    public static URI storeIon(RunContext runContext, List<?> items, String name) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (Object item : items) {
            FileSerde.write(output, item);
        }
        return runContext.storage().putFile(new ByteArrayInputStream(output.toByteArray()), name);
    }
}
