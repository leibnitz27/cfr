package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class SwitchTest1 {

    enum enm {
        ONE,
        TWO,
        THREE
    }

    ;

    public int test0(enm e) {
        switch (e) {
            case ONE:
                return 1;
            case THREE:
                System.out.println("Fallthrough!");
            case TWO:
                return 2;
        }
        return 0;
    }


    // LookupSwitch
    public void test1(int x) {
        switch (x) {
            case 1:
                System.out.println("One");   // Fall through
            case 3:
                System.out.println("Three");
                break;
            case 7:
                System.out.println("Seven");
                break;
            case 11214:
            case 5000:
                System.out.println("FiveK"); // Fall through
            default:
                System.out.println("Default");

        }
    }


    // Tableswitch
    public void test2(int x) {
        switch (x) {
            case 1:
                System.out.println("One");   // Fall through
            case 3:
                System.out.println("Three");
                break;
            case 6:
            case 7:
                System.out.println("Seven");
                break;
            case 5:
                System.out.println("Five"); // Fall through
            default:
                System.out.println("Default");

        }
    }

}
