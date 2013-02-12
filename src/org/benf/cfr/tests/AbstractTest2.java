package org.benf.cfr.tests;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/02/2013
 * Time: 17:44
 */
public abstract class AbstractTest2<T> {
    public abstract T create(List<String> args, Map<String, String> opts);
}
