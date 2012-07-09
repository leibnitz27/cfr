package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class SwitchTest3 {

    enum enm {
        ONE,
        TWO,
        THREE,
        FOUR
    }

    ;

    public int test0(enm e) {
        fred:
        for (int x = 0; x < 10; ++x) {
            switch (e) {
                default:
                    System.out.println("Test");
                    break;
                case ONE:
                    return 1;
                case TWO:
                    return 2;
                case FOUR:
                    break fred;
                case THREE:
                    break fred;
            }
            System.out.println("Here");
        }
        return 1;
    }


}
