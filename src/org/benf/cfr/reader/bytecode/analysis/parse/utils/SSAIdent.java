package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import java.util.BitSet;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 26/06/2012
 * Time: 06:41
 */
public class SSAIdent {
    private final BitSet val;

    public SSAIdent(int idx) {
        val = new BitSet();
        val.set(idx);
    }

    private SSAIdent(BitSet content) {
        this.val = content;
    }

    public SSAIdent mergeWith(SSAIdent other) {
        BitSet b1 = val;
        BitSet b2 = other.val;
        if (b1.equals(b2)) return this;
        b1 = (BitSet) b1.clone();
        b1.or(b2);
        return new SSAIdent(b1);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof SSAIdent)) return false;
        SSAIdent other = (SSAIdent) o;
        return val.equals(other.val);
    }

    @Override
    public int hashCode() {
        return val.hashCode();
    }

    @Override
    public String toString() {
        return val.toString();
    }
}
