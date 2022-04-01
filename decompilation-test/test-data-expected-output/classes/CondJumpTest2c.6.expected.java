/*
 * Decompiled with CFR.
 */
package org.benf.cfr.tests;

public class CondJumpTest2c {
    public boolean test(boolean bl, boolean bl2) {
        boolean bl3;
        return bl2 && bl == (bl3 = bl2) || bl2 && (bl3 = bl);
    }
}
