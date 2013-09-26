package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 25/09/2013
 * Time: 06:47
 */
public enum DynamicInvokeType {
    UNKNOWN("?"),
    METAFACTORY_1("metaFactory"),
    METAFACTORY_2("metafactory"),
    ALTMETAFACTORY_1("altMetaFactory"),
    ALTMETAFACTORY_2("altMetafactory");

    private final String constName;

    private DynamicInvokeType(String constName) {
        this.constName = constName;
    }

    public String getConstName() {
        return constName;
    }

    public static DynamicInvokeType lookup(String name) {
        if (name.equals(METAFACTORY_1.constName)) return METAFACTORY_1;
        if (name.equals(METAFACTORY_2.constName)) return METAFACTORY_2;
        if (name.equals(ALTMETAFACTORY_1.constName)) return ALTMETAFACTORY_1;
        if (name.equals(ALTMETAFACTORY_2.constName)) return ALTMETAFACTORY_2;
        return UNKNOWN;
    }
}
