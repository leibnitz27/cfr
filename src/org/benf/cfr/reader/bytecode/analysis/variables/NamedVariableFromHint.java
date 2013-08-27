package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 21/08/2013
 * Time: 18:23
 */
public class NamedVariableFromHint implements NamedVariable {
    private String name;
    private int slot;

    public NamedVariableFromHint(String name, int slot) {
        this.name = name;
        this.slot = slot;
    }

    @Override
    public void forceName(String name) {
        this.name = name;
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
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NamedVariableFromHint that = (NamedVariableFromHint) o;

        if (slot != that.slot) return false;
        if (!name.equals(that.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + slot;
        return result;
    }
}
