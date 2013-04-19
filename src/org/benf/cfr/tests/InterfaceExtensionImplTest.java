package org.benf.cfr.tests;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2013
 * Time: 18:21
 */
public class InterfaceExtensionImplTest implements InterfaceExtensionTest {
    @Override
    public void test1() {
        System.out.println("Fred");
        test2();
    }

}
