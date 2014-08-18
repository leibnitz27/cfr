package org.benf.cfr.reader;

import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.functors.NonaryFunction;

import java.util.HashMap;
import java.util.List;

/*
 * Because we're used against potentially experimental versions of the jdk, let's check to see if
 * we can spot any known issues.
 */
public class BugCheck {
    public static NonaryFunction<Void> checkKnownJreBugs() {
        final List<String> bugs = ListFactory.newList();
        checkNPE(bugs);
        if (bugs.isEmpty()) return new NonaryFunction<Void>() {
            @Override
            public Void invoke() {
                return null;
            }
        };

        return new NonaryFunction<Void>() {
            @Override
            public Void invoke() {
                System.err.println("//*********** [[ WARNING ]] ***************");
                for (String err : bugs) {
                    System.err.println("// " + err);
                }
                System.err.println("//*********** [[ WARNING ]] ***************");
                return null;
            }
        };
    }

    private static void checkNPE(List<String> errs) {
        HashMap<Integer, Integer> test = new HashMap<Integer, Integer>();
        for (int idx = 0; idx< 9000;idx+=32) {
            test.put(idx, idx);
        }
        try {
            test.put(null, null);
        } catch (NullPointerException e) {
            errs.add("The JRE you are using has a buggy implementation of HashMap.  CFR may not work correctly.");
        }
    }
}
