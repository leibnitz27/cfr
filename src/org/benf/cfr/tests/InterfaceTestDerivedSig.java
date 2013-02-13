package org.benf.cfr.tests;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/02/2013
 * Time: 17:44
 */

import java.util.List;
import java.util.Map;

public interface InterfaceTestDerivedSig<A extends Integer> extends InterfaceTestBase, InterfaceTestBaseSig<String, A, Map<String, A>> {
    void test3();

    void doit(A a, List<? super A> x);
}
