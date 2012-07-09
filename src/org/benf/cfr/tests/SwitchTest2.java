package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class SwitchTest2 {

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
        }
        return 0;
    }


}
