package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * See table 3.3 in JVM spec.
 */
public enum StackType {
    INT("int", 1, true),
    FLOAT("float", 1, true),
    REF("reference", 1, false),
    RETURNADDRESS("returnaddress", 1, false),
    RETURNADDRESSORREF("returnaddress or ref", 1, false), // Special, for astore.
    LONG("long", 2, true),
    DOUBLE("double", 2, true),
    VOID("void", 0, false);   // not real, but useful.

    private final String name;
    private final int computationCategory;
    private final StackTypes asList;
    private final boolean closed;

    private StackType(String name, int computationCategory, boolean closed) {
        this.name = name;
        this.computationCategory = computationCategory;
        this.asList = new StackTypes(this);
        this.closed = closed;
    }

    public int getComputationCategory() {
        return computationCategory;
    }

    public StackTypes asList() {
        return asList;
    }

    public boolean isClosed() {
        return closed;
    }
}
