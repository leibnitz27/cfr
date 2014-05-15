package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

import java.util.List;

public class MatchIterator<T> {
    private final List<T> data;
    private int idx;

    public MatchIterator(List<T> data) {
        this.data = data;
        this.idx = -1;
    }

    private MatchIterator(List<T> data, int idx) {
        this.data = data;
        this.idx = idx;
    }

    public T getCurrent() {
        if (idx < 0) throw new IllegalStateException("Accessed before being advanced.");
        if (idx >= data.size()) {
            throw new IllegalStateException("Out of range");
        }
        return data.get(idx);
    }

    public MatchIterator<T> copy() {
        return new MatchIterator<T>(data, idx);
    }

    public void advanceTo(MatchIterator<StructuredStatement> other) {
        if (data != other.data) throw new IllegalStateException(); // ref check.
        this.idx = other.idx;
    }

    public boolean hasNext() {
        return idx < data.size() - 1;
    }

    public boolean isFinished() {
        return idx >= data.size();
    }

    public int getRemaining() {
        return data.size() - idx;
    }

    public boolean advance() {
        if (!isFinished()) idx++;
        return !isFinished();
    }

    public void rewind1() {
        if (idx > 0) idx--;
    }

    @Override
    public String toString() {
        if (isFinished()) return "Finished";
        T t = data.get(idx);
        return t == null ? "null" : t.toString();
    }
}
