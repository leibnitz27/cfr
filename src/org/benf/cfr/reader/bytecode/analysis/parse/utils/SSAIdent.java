package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import java.util.BitSet;

public class SSAIdent {
    public static SSAIdent poison = new SSAIdent(0, new Object());

    private final BitSet val;
    private final Object comparisonType;

    public SSAIdent(int idx, Object comparisonType) {
        val = new BitSet();
        val.set(idx);
        this.comparisonType = comparisonType;
    }

    private SSAIdent(BitSet content, Object comparisonType) {
        this.val = content;
        this.comparisonType = comparisonType;
    }

    public Object getComparisonType() {
        return comparisonType;
    }

    public SSAIdent mergeWith(SSAIdent other) {
        BitSet b1 = val;
        BitSet b2 = other.val;
        if (b1.equals(b2)) return this;
        b1 = (BitSet) b1.clone();
        b1.or(b2);
        return new SSAIdent(b1, comparisonType);
    }

    public boolean isSuperSet(SSAIdent other) {
        BitSet tmp = (BitSet) val.clone();
        tmp.or(other.val);
        // if or-ing it changed cardinality, then it wasn't a superset.
        if (tmp.cardinality() != val.cardinality()) return false;
        tmp.xor(other.val);
        return (tmp.cardinality() > 0);
    }

    public int card() {
        return val.cardinality();
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

    public boolean isFirstIn(SSAIdent other) {
        int bit1 = val.nextSetBit(0);
        int bit2 = other.val.nextSetBit(0);
        return bit1 == bit2;
    }
}
