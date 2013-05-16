package org.benf.cfr.tests;

import org.benf.cfr.reader.util.MapFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class MemberTest2 {

    private final int y = 2;
    private final int z;
    private int x = 3;
    private Map<String, String> m = new HashMap<String, String>(x);

    public MemberTest2() {
        this(6);
    }

    public MemberTest2(String s) {
        this.z = s.length();
    }

    public MemberTest2(int z) {
        this.z = z;
    }

    public int inc(int y) {
        return (x += y);
    }
}
