package org.benf.cfr.tests;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class MemberTest3 extends MemberTest2 {

    private final int y = 2;
    private final int z;
    private int x = 3;
    private Map<String, String> m3 = new HashMap<String, String>(x);

    public MemberTest3() {
        this(6);
    }

    public MemberTest3(String s) {
        super(s);
        this.z = s.length();
    }

    public MemberTest3(int z) {
        this.z = z;
    }

    public int inc(int y) {
        return (x += y);
    }
}
