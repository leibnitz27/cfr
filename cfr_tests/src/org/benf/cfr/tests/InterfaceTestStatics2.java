package org.benf.cfr.tests;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/02/2013
 * Time: 17:44
 */

public interface InterfaceTestStatics2 {
    public static String STATIC_STRING = "Here be a static";
    // This is moved to a static block by the compiler, however that's not allowed in
    // interfaces (a static block), so we can't leave it there on decompilation.
    public static Map<String, String> map = new HashMap<String, String>();
}
