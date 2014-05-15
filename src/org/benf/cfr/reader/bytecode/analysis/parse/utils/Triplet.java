package org.benf.cfr.reader.bytecode.analysis.parse.utils;

public class Triplet<X, Y, Z> {
    private final X x;
    private final Y y;
    private final Z z;

    public Triplet(X x, Y y, Z z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public X getFirst() {
        return x;
    }

    public Y getSecond() {
        return y;
    }

    public Z getThird() {
        return z;
    }

    public static <A, B, C> Triplet<A, B, C> make(A a, B b, C c) {
        return new Triplet<A, B, C>(a, b, c);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Triplet)) return false;

        Triplet other = (Triplet) o;

        if (x == null) {
            if (other.x != null) return false;
        } else {
            if (!x.equals(other.x)) return false;
        }
        if (y == null) {
            if (other.y != null) return false;
        } else {
            if (!y.equals(other.y)) return false;
        }
        if (z == null) {
            if (other.z != null) return false;
        } else {
            if (!z.equals(other.z)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        if (x != null) hashCode = x.hashCode();
        if (y != null) hashCode = (hashCode * 31) + y.hashCode();
        if (z != null) hashCode = (hashCode * 31) + z.hashCode();
        return hashCode;
    }
}
