package org.benf.cfr.tests;

import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIConversion;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 18:05
 * To change this template use File | Settings | File Templates.
 */
public class LongTest1 {

    public static int agg1(long a, long b) {
        return Long.signum(a-b);
    }
    
    public static void main(String[] args) {
    }
}
