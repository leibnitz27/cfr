/*
 * Decompiled with CFR.
 */
package org.benf.cfr.tests;

public class TryTest1 {
    public void test1() {
        try {
            try {
                System.out.print(3);
                throw new NoSuchFieldException();
            } catch (NoSuchFieldException noSuchFieldException) {
                System.out.print("Finally!");
            }
        } catch (Throwable throwable) {
            System.out.print("Finally!");
            throw throwable;
        }
        System.out.print(5);
    }
}
