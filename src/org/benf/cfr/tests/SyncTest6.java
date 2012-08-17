package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 */
public class SyncTest6 {

    private int x[];

    /* This doesn't currently work, because we are
     * incorrectly pushing intermediate expression computation
     * past a monitorexit.
     *
     * This needs to be banned.
     *
     *     public int test1();
  Code:
   0:	aload_0
   1:	dup
   2:	astore_1
   3:	monitorenter
   4:	aload_0
   5:	getfield	#2; //Field x:[I
   8:	iconst_2
   9:	iaload
   10:	aload_1
   11:	monitorexit
   12:	ireturn <<-- the return is after the exit, but we're pushing the computed value here.
   13:	astore_2
   14:	aload_1
   15:	monitorexit
   16:	aload_2
   17:	athrow

     */
    public int test1() {
        synchronized (this) {
            return x[2];
        }
    }
}
