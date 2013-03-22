package org.benf.cfr.tests;

import com.sun.istack.internal.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * To change this template use File | Settings | File Templates.
 */
public class AnnotationTest1 {

    @Deprecated
    void foo(int x) {
    }

    @AnnotationTestAnnotation(value = {"fred", "jim"}, fred = 1)
    @AnnotationTestAnnotation2("fred")
    void foo(int x, @Nullable Double y) {
        System.out.println("Foo!");
    }
}
