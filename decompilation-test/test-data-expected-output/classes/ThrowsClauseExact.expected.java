/*
 * Decompiled with CFR.
 */
package org.benf.cfr.tests;

class ThrowsClauseExact<E extends Exception> {
    ThrowsClauseExact() {
    }

    public void testThrow() throws Exception, Exception, Exception, RuntimeException, RuntimeException {
    }

    public static void main(String ... args) throws Exception {
        System.out.println(ThrowsClauseExact.class.getMethod("testThrow", new Class[0]).getExceptionTypes().length);
    }
}
