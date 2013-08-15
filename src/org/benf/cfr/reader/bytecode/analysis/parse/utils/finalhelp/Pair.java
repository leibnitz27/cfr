package org.benf.cfr.reader.bytecode.analysis.parse.utils.finalhelp;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 12/08/2013
 * Time: 21:51
 */
class Pair {
    public final Op03SimpleStatement a;
    public final Op03SimpleStatement b;

    public Pair(Op03SimpleStatement a, Op03SimpleStatement b) {
        this.a = a;
        this.b = b;
    }
}