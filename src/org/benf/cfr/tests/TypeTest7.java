package org.benf.cfr.tests;

public class TypeTest7 {

    // Test 2 and test 3 produce EXACTLY the same bytecode
    public int test2(int x) {
        boolean a = false;
        if (x > 4) a = true;
        return a ? 5 : 2;
    }

    // ....
    public int test3(int x) {
        int a = 0;
        if (x > 4) a = 1;
        return a != 0 ? 5 : 2;
    }
}
