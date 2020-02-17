package org.benf.cfr.reader.mapping;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;

import java.util.List;

public class MethodMapping {
    private final String name;
    private final String rename;
    private final JavaTypeInstance res;
    private final List<JavaTypeInstance> argTypes;

    public MethodMapping(String rename, String name, JavaTypeInstance res, List<JavaTypeInstance> argTypes) {
        this.name = name;
        this.rename = rename;
        this.res = res;
        this.argTypes = argTypes;
    }

    public String getName() {
        return name;
    }

    public String getRename() {
        return rename;
    }

    public JavaTypeInstance getResultType() {
        return res;
    }

    public List<JavaTypeInstance> getArgTypes() {
        return argTypes;
    }
}
