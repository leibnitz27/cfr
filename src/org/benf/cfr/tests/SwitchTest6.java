package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class SwitchTest6 {

    enum enm {
        ONE,
        TWO,
        THREE,
        WIBBLE
    }

    ;

    public int test0(enm e) {
        switch (e) {
            case ONE:
                return 1;
            case THREE:
            case WIBBLE:
                System.out.println("Fallthrough!");
            case TWO:
                return 2;
        }
        return 0;
    }

}
