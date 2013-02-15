package org.benf.cfr.tests;

public class TypeTest8 {

    // Test 2 and test 3 produce EXACTLY the same bytecode
    public void test2(int x) {
        for (boolean b : new boolean[]{true, false}) {
            System.out.println(b);
        }
    }

    // ....
    public void test3(int x) {
        for (int b : new int[]{1, 0}) {
            System.out.println(b);
        }
    }
}
