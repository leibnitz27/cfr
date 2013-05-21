package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class CondJumpTest1b {


    /*
     *
           0:	iload_1
           1:	ifne	12     If a then goto 12
           4:	iload_2
           5:	ifeq	16     if !b then goto 16
           8:	iload_3
           9:	ifeq	16     if !c then goto 16
           12:	iconst_1       return 1
           13:	goto	17
           16:	iconst_0       return 0
           17:	ireturn

       a && b ->

       a -> return 1
       !b -> return 0
       !c -> return 0
       return 1


     */

    public boolean test (boolean  a, boolean  b, boolean c)
    {
        return  (a || (b && c ));
    }


}
