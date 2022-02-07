/*
 * Decompiled with CFR.
 */
package org.benf.multijar2;

public class Main {
    public static void main(String ... args) {
        Main main = new Main();
        main.getClass();
        main.new Fred().doIt();
    }

    /*
     * Multiple versions of this class in jar - see https://www.benf.org/other/cfr/multi-version-jar.html
     */
    private class Fred {
        private Fred() {
        }

        public void doIt() {
            System.out.println("BIG secret");
        }
    }
}

