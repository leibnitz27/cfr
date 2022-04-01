/*
 * Decompiled with CFR.
 */
package org.benf.cfr.tests;

public class CondJumpTest2c {
    public boolean test(boolean a, boolean b) {
        boolean c;
        return b && a == (c = b) || b && (c = a);
    }
}
