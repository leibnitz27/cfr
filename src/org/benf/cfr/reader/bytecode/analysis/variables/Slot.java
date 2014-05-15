package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;

public class Slot {
    private final JavaTypeInstance javaTypeInstance;
    private final int idx;

    public Slot(JavaTypeInstance javaTypeInstance, int idx) {
        this.javaTypeInstance = javaTypeInstance;
        this.idx = idx;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Slot slot = (Slot) o;

        if (idx != slot.idx) return false;

        return true;
    }

    public int getIdx() {
        return idx;
    }

    public JavaTypeInstance getJavaTypeInstance() {
        return javaTypeInstance;
    }

    @Override
    public String toString() {
        return "S{" +
                idx +
                '}';
    }

    @Override
    public int hashCode() {
        return idx;
    }
}
