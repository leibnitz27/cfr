package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/08/2012
 * Time: 06:53
 */
public enum WildcardType {
    NONE(""),
    SUPER("super"),
    EXTENDS("extends");

    private final String name;

    WildcardType(String name) {
        this.name = name;
    }


    @Override
    public String toString() {
        return name;
    }
}
