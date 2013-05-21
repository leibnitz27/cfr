package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class SwitchTest2 {

    public int test0(EnumTest1 e) {
        switch (e) {
            case FOO:
                return 1;
            case BAP:
                return 2;
        }
        return 0;
    }

}
