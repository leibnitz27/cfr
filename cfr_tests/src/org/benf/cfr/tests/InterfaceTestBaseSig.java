package org.benf.cfr.tests;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/02/2013
 * Time: 17:44
 */

import java.util.Map;

public interface InterfaceTestBaseSig<A, B, T extends Map<A, B>> {
    T test2(T arg);
}
