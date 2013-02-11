package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class EnumSwitchTest1 {

    enum enm {
        ONE,
        TWO,
        THREE,
        FOUR
    }

    public int test0(enm e) {
        switch (e) {
            default:
                System.out.println("Test");
                break;
            case ONE:
                return 9;
            case TWO:
                return 2;
        }
        System.out.println("Here");
        return 1;
    }


}
