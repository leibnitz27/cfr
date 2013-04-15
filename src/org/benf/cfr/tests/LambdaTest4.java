package org.benf.cfr.tests;

import org.benf.cfr.reader.util.functors.BinaryFunction;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class LambdaTest4 {

    private int x;

    public LambdaTest4(int x) {
        this.x = x;
    }

    Integer invoker(int arg, BinaryFunction<Integer, String, Integer> fn) {
        return fn.invoke(arg, "Fre");
    }

    public int test(int y, Object o) {
        return invoker(3, (x, z) -> 2 + x + y + 1 + z.length() + this.x);
    }

}
