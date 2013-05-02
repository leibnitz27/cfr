package org.benf.cfr.tests;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/02/2013
 * Time: 17:44
 */

public interface InterfaceTestMembers {
    public final Map<String, Integer> z = new HashMap<String, Integer>();
    public int x = 4; // what does this even mean?  It's not final....
}
