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
public class StaticInitTest3 {

    static int x = 5;
    static Map<String, String> map = new HashMap<String, String>(x);
}
