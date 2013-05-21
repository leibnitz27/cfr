package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * <p/>
 * http://www.program-transformation.org/Transform/DecompilerControlFlowTestSource
 * <p/>
 * (some decompilers fail to get the inner while.  (cfr did at 0_3).
 */
public class ControlFlowTest2 {
    public int foo(int i, int j) {
        while (true) {
//            System.out.println("b");
            if (i++ < 5) {
                System.out.println("a");
            } else if (i < 10) {
//                System.out.println("F");
                continue;
            } else {
                continue;
            }
//            System.out.println("Fred");
            break;
        }
        return j;
    }
}
