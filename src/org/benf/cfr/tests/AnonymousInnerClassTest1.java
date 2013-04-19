package org.benf.cfr.tests;

import org.benf.cfr.reader.util.functors.UnaryFunction;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/04/2013
 * Time: 17:49
 */
public class AnonymousInnerClassTest1 {

    Integer invoker(int arg, UnaryFunction<Integer, Integer> fn) {
        return fn.invoke(arg);
    }

    public int doit(int x) {
        return invoker(x, new UnaryFunction<Integer, Integer>() {
            @Override
            public Integer invoke(Integer arg) {
                return arg * 3;
            }
        });
    }

}
