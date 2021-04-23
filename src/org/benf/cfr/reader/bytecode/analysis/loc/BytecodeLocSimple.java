package org.benf.cfr.reader.bytecode.analysis.loc;

import org.benf.cfr.reader.entities.Method;

import java.util.Collection;
import java.util.Collections;

class BytecodeLocSimple extends BytecodeLoc {
    private final int offset;
    private Method method;

    /* Lambdas & other helper methods make keeping track of bytecode location much, much
     * more painful.  We inline lambdas, but that's not reasonable to do with bytecode
     * locations, as they are with reference to the extracted method.
     * As such, we have to track both the offset, and the method this is an offset within!
     */
    BytecodeLocSimple(int offset, Method method) {
        this.offset = offset;
        this.method = method;
    }

    @Override
    void addTo(BytecodeLocCollector collector) {
        collector.add(method, offset);
    }

    @Override
    public String toString() {
        return Integer.toString(offset);
    }

    @Override
    public Collection<Method> getMethods() {
        return Collections.singleton(method);
    }

    @Override
    public Collection<Integer> getOffsetsForMethod(Method method) {
        return Collections.singleton(offset);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
