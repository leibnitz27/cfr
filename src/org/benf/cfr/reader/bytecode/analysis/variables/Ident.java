package org.benf.cfr.reader.bytecode.analysis.variables;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 23/08/2013
 * Time: 17:47
 */
public class Ident {
    private final int stackpos;
    private final int idx;

    public Ident(int stackpos, int idx) {
        this.stackpos = stackpos;
        this.idx = idx;
    }

    @Override
    public String toString() {
        if (idx == 0) return "" + stackpos;
        return "" + stackpos + "_" + idx;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Ident ident = (Ident) o;

        if (idx != ident.idx) return false;
        if (stackpos != ident.stackpos) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = stackpos;
        result = 31 * result + idx;
        return result;
    }
}
