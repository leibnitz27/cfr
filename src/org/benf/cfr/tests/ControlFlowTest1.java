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
public class ControlFlowTest1 {
    public int foo(int i, int j) {
        while (true) {
            try {
                while (i < j)
                    i = j++ / i;
            } catch (RuntimeException re) {
                i = 10;
                continue;
            }
            break;
        }
        return j;
    }
}
