package org.benf.cfr.reader.util;

import java.util.Stack;

public class StackFactory {
    public static <X extends Object> Stack<X> newStack() {
        return new Stack<X>();
    }
}
