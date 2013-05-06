package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class ConstructorChainTest2 extends ConstructorChainTest1 {

    public ConstructorChainTest2(int x, int y) {
        super(x, y);
    }

    public ConstructorChainTest2() {
        super(3, 4);
    }

    public ConstructorChainTest2(int x) {
        this(3, 4);
    }

    @Override
    public int testFn() {
        return 12;
    }

    public int test1() {
        // generates invokevirtual
        return testFn();
    }

    public int test2() {
        // generates invokespecial
        return super.testFn();
    }

    private int testx() {
        return 3;
    }

    public int test3(ConstructorChainTest2 other) {
        // generates invokespecial
        return other.testx();
    }
}
