package org.benf.cfr.reader.bytecode.analysis.opgraph;

import java.util.LinkedList;

public class InstrIndex implements Comparable<InstrIndex> {
    private final int index;
    //    private final int subindex; // for renumbering, etc.
    private TempRelatives tempList;

    public InstrIndex(int index) {
        this.index = index;
//        this.subindex = 0;
        this.tempList = null;
    }

    private InstrIndex(int index, TempRelatives tempList) {
        this.index = index;
        this.tempList = tempList;
    }

    private int idx() {
        if (tempList == null) return 0;
        return tempList.indexOf(this);
    }

    @Override
    public String toString() {
        int subidx = idx();
        return "lbl" + index + (subidx == 0 ? "" : "." + subidx);
    }

    @Override
    public int compareTo(InstrIndex other) {
        int a = index - other.index;
        if (a != 0) return a;
        if (tempList != other.tempList) {
            throw new IllegalStateException("Bad templists");
        }
        a = idx() - other.idx();
        return a;
    }

    // NOTE DELIBERATE USE OF OBJECT HASH AND EQUALS.
    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    private void mkTempList() {
        if (tempList == null) {
            tempList = new TempRelatives(this);
        }
    }

    public InstrIndex justBefore() {
        mkTempList();
        InstrIndex res = new InstrIndex(this.index, tempList);
        tempList.before(this, res);
        return res;
    }

    public InstrIndex justAfter() {
        mkTempList();
        InstrIndex res = new InstrIndex(this.index, tempList);
        tempList.after(this, res);
        return res;
    }

    public boolean directlyPreceeds(InstrIndex other) {
        return this.index == other.index - 1;
    }

    public boolean isBackJumpTo(IndexedStatement other) {
        return isBackJumpTo(other.getIndex()) < 0;
    }

    private int isBackJumpTo(InstrIndex other) {
        return Integer.signum(other.compareTo(this));
    }

    public boolean isBackJumpFrom(IndexedStatement other) {
        return isBackJumpFrom(other.getIndex());
    }

    public boolean isBackJumpFrom(InstrIndex other) {
        return isBackJumpTo(other) > 0;
    }


    private static class TempRelatives {
        private final LinkedList<InstrIndex> rels = new LinkedList<InstrIndex>();

        public TempRelatives(InstrIndex start) {
            rels.add(start);
        }

        public int indexOf(InstrIndex i) {
            return rels.indexOf(i);
        }

        public void before(InstrIndex than, InstrIndex isBefore) {
            int idx = rels.indexOf(than);
            rels.add(idx, isBefore);
        }

        public void after(InstrIndex than, InstrIndex isBefore) {
            int idx = rels.indexOf(than);
            rels.add(idx + 1, isBefore);
        }
    }
}
