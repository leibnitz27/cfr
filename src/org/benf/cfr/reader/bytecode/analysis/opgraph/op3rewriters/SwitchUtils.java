package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CaseStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.collections.SetUtil;

import java.util.List;

class SwitchUtils {
    static void checkFixNewCase(Op03SimpleStatement possCaseItem, Op03SimpleStatement possCase) {
        if (possCase.getStatement().getClass() != CaseStatement.class) return;
        List<BlockIdentifier> idents = SetUtil.differenceAtakeBtoList(possCaseItem.getBlockIdentifiers(), possCase.getBlockIdentifiers());
        idents = Functional.filter(idents, new Predicate<BlockIdentifier>() {
            @Override
            public boolean test(BlockIdentifier in) {
                return in.getBlockType() == BlockType.CASE;
            }
        });
        if (idents.isEmpty()) {
            BlockIdentifier blockIdentifier = ((CaseStatement)possCase.getStatement()).getCaseBlock();
            possCaseItem.getBlockIdentifiers().add(blockIdentifier);
        }
    }
}
