package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.util.ConfusedCFRException;

import java.util.Comparator;

public class CompareByIndex implements Comparator<Op03SimpleStatement> {

    private boolean asc;

    public CompareByIndex() {
        this(true);
    }

    public CompareByIndex(boolean asc) {
        this.asc = asc;
    }

    @Override
    public int compare(Op03SimpleStatement a, Op03SimpleStatement b) {
        int res = a.getIndex().compareTo(b.getIndex());
        if (!asc) res = -res;
        if (res == 0) {
            throw new ConfusedCFRException("Can't sort instructions [" + a + ", " + b + "]");
        }
        //noinspection ComparatorMethodParameterNotUsed
        return res;
    }
}
