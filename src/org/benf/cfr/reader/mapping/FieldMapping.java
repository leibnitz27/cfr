package org.benf.cfr.reader.mapping;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;

public class FieldMapping {
    private final String name;
    private final String rename;
    private final JavaTypeInstance type;

    FieldMapping(String rename, String name, JavaTypeInstance type) {
        this.name = name;
        this.rename = rename;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getRename() {
        return rename;
    }

    public JavaTypeInstance getType() {
        return type;
    }
}
