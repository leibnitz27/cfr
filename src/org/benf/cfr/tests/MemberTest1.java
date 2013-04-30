package org.benf.cfr.tests;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
public class MemberTest1 {

    private int x;

    public MemberTest1(int x) {
        this.x = x;
    }

    public int inc(int y) {
        return (x += y);
    }
}
