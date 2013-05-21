package org.benf.cfr.tests;


import org.benf.cfr.tests.support.SetFactory;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/09/2012
 * Time: 07:09
 */
public class InnerClassTest10 {

    public class Inner1 {

        public InnerClassTest4.InnerBase tweak(int y) {
            return new InnerClassTest4.InnerBase(SetFactory.newSet(y));
        }

    }
}
