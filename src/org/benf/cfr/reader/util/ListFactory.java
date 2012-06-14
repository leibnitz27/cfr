package org.benf.cfr.reader.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 09/03/2012
 * Time: 17:51
 * To change this template use File | Settings | File Templates.
 */
public class ListFactory {
    public static <X extends Object> List<X> newList() {
        return new ArrayList<X>();
    }

    public static <X extends Object> List<X> newList(X[] original) {
        return Arrays.asList(original);
    }

    public static <X extends Object> List<X> newList(List<X> original) {
        return new ArrayList<X>(original);
    }

    public static <X extends Object> LinkedList<X> newLinkedList() {
        return new LinkedList<X>();
    }
}
