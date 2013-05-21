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
public class ControlFlowTest8 {
    public int foo(int i, int j) {
        do {
            i++;
            continue;
        } while (i < j);
        return j;
    }
}
