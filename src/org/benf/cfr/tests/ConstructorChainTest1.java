package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class ConstructorChainTest1 {

    private final int x;
    private final int y;

    public ConstructorChainTest1(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public ConstructorChainTest1() {
        this(1, 3);
    }

    public ConstructorChainTest1(int x) {
        this();
    }

    public int testFn() {
        return x;
    }
}
