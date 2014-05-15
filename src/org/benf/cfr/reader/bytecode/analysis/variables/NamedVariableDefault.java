package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.util.output.Dumper;

public class NamedVariableDefault implements NamedVariable {
    private String name;
    private boolean isGoodName = false;

    public NamedVariableDefault(String name) {
        this.name = name;
    }

    @Override
    public void forceName(String name) {
        this.name = name;
        isGoodName = true;
    }

    @Override
    public String getStringName() {
        return name;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print(name);
    }

    @Override
    public boolean isGoodName() {
        return isGoodName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NamedVariableDefault that = (NamedVariableDefault) o;

        if (!name.equals(that.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
