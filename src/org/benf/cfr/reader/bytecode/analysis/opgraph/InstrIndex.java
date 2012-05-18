package org.benf.cfr.reader.bytecode.analysis.opgraph;

/**
 * Created:
 * User: lee
 * Date: 27/04/2012
 */
public class InstrIndex implements Comparable<InstrIndex> {
    private final int index;
    private final int subindex; // for renumbering, etc.

    public InstrIndex(int index) {
        this.index = index;
        this.subindex = 0;
    }

    private InstrIndex(int index, int subindex) {
        this.index = index;
        this.subindex = subindex;
    }

    @Override
    public String toString() {
        return "lbl" + index + (subindex == 0 ? "" : ("." + subindex));
    }

    @Override
    public int compareTo(InstrIndex other) {
        int a = index - other.index;
        if (a != 0) return a;
        a = subindex - other.subindex;
        return a;
    }

    @Override
    public int hashCode() {
        return index + (1001 * subindex);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InstrIndex)) return false;
        InstrIndex other = (InstrIndex) o;
        return this.index == other.index && this.subindex == other.subindex;
    }

    public InstrIndex justBefore() {
        return new InstrIndex(this.index, this.subindex - 1);
    }

    public InstrIndex justAfter() {
        return new InstrIndex(this.index, this.subindex + 1);
    }

    public boolean directlyPreceeds(InstrIndex other) {
        return this.index == other.index - 1;
    }


}
