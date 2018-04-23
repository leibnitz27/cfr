package org.benf.cfr.reader.util;

import org.benf.cfr.reader.util.functors.UnaryProcedure;

public class Optional<T> {
    private final T value;
    private final boolean set;
    private static final Optional Empty = new Optional();

    private Optional(T val) {
        value = val;
        set = true;
    }

    private Optional() {
        set = false;
        value = null;
    }

    public boolean isSet() {
        return set;
    }

    public T getValue() {
        return value;
    }

    public void then(UnaryProcedure<T> func) {
        func.call(value);
    }

    public static <T> Optional<T> of(T value) {
        return new Optional<T>(value);
    }

    public static <T> Optional<T> empty() {
        return (Optional<T>)Optional.Empty;
    }
}
