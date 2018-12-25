package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.functors.Predicate;

import java.util.List;

public class UselessNops {
    public static List<Op03SimpleStatement> removeUselessNops(List<Op03SimpleStatement> in) {
        return Functional.filter(in, new Predicate<Op03SimpleStatement>() {
            @Override
            public boolean test(Op03SimpleStatement in) {
                return !(in.getSources().isEmpty() && in.getTargets().isEmpty());
            }
        });
    }
}
