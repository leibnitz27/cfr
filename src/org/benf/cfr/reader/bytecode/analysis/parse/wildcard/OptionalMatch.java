package org.benf.cfr.reader.bytecode.analysis.parse.wildcard;

import org.benf.cfr.reader.util.Optional;

public class OptionalMatch<T> {
    final Optional<T> expected; // may be missing.
    T matched;

    OptionalMatch(Optional<T> expected) {
        this.expected = expected;
        reset();
    }

    public boolean match(T other) {
        if (matched != null) return matched.equals(other);
        matched = other;
        return true;
    }

    public void reset() {
        if (expected.isSet()) {
            matched = expected.getValue();
        } else {
            matched = null;
        }
    }

    public T getMatch() {
        return matched;
    }
}
