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

    void advanceTo(MatchIterator<StructuredStatement> other) {
        if (data != other.data) throw new IllegalStateException(); // ref check.
        this.idx = other.idx;
    }

    public boolean hasNext() {
        return idx < data.size() - 1;
    }

    private boolean isFinished() {
        return idx >= data.size();
    }

    public boolean advance() {
        if (!isFinished()) idx++;
        return !isFinished();
    }

    public void rewind1() {
        if (idx > 0) idx--;
    }

    /*
     * toString on this should only be visible during diagnostics.
     * don't dump too much, as this can be used on *very* large data.
     */
    @Override
    public String toString() {
        if (data == null) return "Null data!"; // precondition fail.
        if (isFinished()) return "Finished";

        StringBuilder sb = new StringBuilder();
        int dumpIdx = idx;
        sb.append(idx).append("/").append(data.size()).append(" ");
        if (dumpIdx == -1) {
            sb.append("(not yet advanced)");
            dumpIdx = 0;
        }
        int start = Math.max(0, dumpIdx - 3);
        int end = Math.min(data.size(), dumpIdx + 3);
        sb.append("[");
        if (start > 0) sb.append("...");
        for (int i = start; i < end; i++) {
            if (i != start) sb.append(",");
            T t = data.get(i);
            sb.append(i).append("#").append(t.getClass().getSimpleName()).append("@").append(Integer.toHexString(t.hashCode()));
        }
        if (end < data.size()) sb.append("...");
        sb.append("]");
        return sb.toString();
    }

    public void rewind() {
        idx = 0;
    }
}
