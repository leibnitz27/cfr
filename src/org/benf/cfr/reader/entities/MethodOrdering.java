package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.entities.attributes.AttributeCode;
import org.benf.cfr.reader.entities.attributes.AttributeLineNumberTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/*
 * Sort methods by line number, if there's an attribute table, if not, retain the order
 */
public class MethodOrdering  {

    private static class OrderData implements Comparable<OrderData> {
        private final Method method;
        private final boolean hasLineNumber;
        private final int origIdx;

        private OrderData(Method method, boolean hasLineNumber, int origIdx){
            this.method = method;
            this.hasLineNumber = hasLineNumber;
            this.origIdx = origIdx;
        }

        @Override
        public int compareTo(OrderData o) {
            if (hasLineNumber != o.hasLineNumber) {
                return hasLineNumber ? -1 : 1;
            }
            return origIdx - o.origIdx;
        }
    }

    public static List<Method> sort(List<Method> methods) {
        List<OrderData> od = new ArrayList<OrderData>();
        boolean hasLineNumbers = false;
        for (int x=0,len=methods.size();x<len;++x) {
            Method method = methods.get(x);
            boolean hasLineNumber = false;
            int idx = x;
            AttributeCode codeAttribute = method.getCodeAttribute();
            if (codeAttribute != null) {
                AttributeLineNumberTable lineNumberTable = codeAttribute.getLineNumberTable();
                if (lineNumberTable != null && lineNumberTable.hasEntries()) {
                    hasLineNumber = true;
                    hasLineNumbers = true;
                    idx = lineNumberTable.getStartLine();
                }
            }
            od.add(new OrderData(method, hasLineNumber, idx));
        }
        if (!hasLineNumbers) return methods;
        Collections.sort(od);
        List<Method> res = new ArrayList<Method>(methods.size());
        for (OrderData o : od) {
            res.add(o.method);
        }
        return res;
    }

 }
