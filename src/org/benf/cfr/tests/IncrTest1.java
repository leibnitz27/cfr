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
public class IncrTest1 {
    public int postincr(int i, int j) {
        j = i++ / 3;
        return j;
    }

    public int preincr(int i, int j) {
        j = ++i / 3;
        return j;
    }

    public int preincr2(int i, int j) {
        j = (i += 3) / 3;
        return j;
    }

}
