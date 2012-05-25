package org.benf.cfr.reader.bytecode.analysis.parse.utils;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 04/04/2012
 */
public class Pair<X, Y> {
    private final X x;
    private final Y y;

    public Pair(X x, Y y) {
        this.x = x;
        this.y = y;
    }

    public X getFirst() {
        return x;
    }

    public Y getSecond() {
        return y;
    }

    public static <A, B> Pair<A, B> make(A a, B b) {
        return new Pair<A, B>(a, b);
    }
}
