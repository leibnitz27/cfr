package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * See table 3.3 in JVM spec.
 */
public enum StackType {
    INT("int", 1),
    FLOAT("float", 1),
    REF("reference", 1),
    RETURNADDRESS("returnaddress", 1),
    RETURNADDRESSORREF("returnaddress or ref", 1), // Special, for astore.
    LONG("long", 2),
    DOUBLE("double", 2),
    VOID("void", 0);   // not real, but useful.

    private final String name;
    private final int computationCategory;
    private final StackTypes asList;

    private StackType(String name, int computationCategory) {
        this.name = name;
        this.computationCategory = computationCategory;
        this.asList = new StackTypes(this);
    }

    public int getComputationCategory() {
        return computationCategory;
    }

    public StackTypes asList() {
        return asList;
    }
}
