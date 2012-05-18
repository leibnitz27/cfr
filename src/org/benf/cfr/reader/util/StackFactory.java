package org.benf.cfr.reader.util;

import java.util.Stack;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 09/03/2012
 * Time: 17:51
 * To change this template use File | Settings | File Templates.
 */
public class StackFactory {
    public static <X extends Object> Stack<X> newStack() {
        return new Stack<X>();
    }
}
