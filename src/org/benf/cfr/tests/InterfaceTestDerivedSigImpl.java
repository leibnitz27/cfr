package org.benf.cfr.tests;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/02/2013
 * Time: 17:44
 */

import java.util.List;
import java.util.Map;

public class InterfaceTestDerivedSigImpl implements InterfaceTestDerivedSig<Integer> {

    @Override
    public void test3(Integer integer) {

    }

    @Override
    public Map<String, Integer> test2(Map<String, Integer> arg) {
        return null;
    }

    @Override
    public void doit(Integer integer, List<? super Integer> x) {
    }

    public void doit2(Integer integer, List<? super Integer> x) {
        x = x;
    }

    @Override
    public void test1() {

    }
}
