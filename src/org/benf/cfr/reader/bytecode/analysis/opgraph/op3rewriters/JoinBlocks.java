package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CatchStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.entities.exceptions.ExceptionGroup;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.List;
import java.util.Set;

public class JoinBlocks {
    /*
     * This is a dangerous tidy-up operation.  Should only do it if we're falling back.
     */
    public static void rejoinBlocks(List<Op03SimpleStatement> statements) {
        Set<BlockIdentifier> lastBlocks = SetFactory.newSet();
        Set<BlockIdentifier> haveLeft = SetFactory.newSet();
        // We blacklist blocks we can't POSSIBLY be in - i.e. after a catch block has started, we can't POSSIBLY
        // be in its try block.
        Set<BlockIdentifier> blackListed = SetFactory.newSet();

        for (int x = 0, len = statements.size(); x < len; ++x) {
            Op03SimpleStatement stm = statements.get(x);
            Statement stmInner = stm.getStatement();
            if (stmInner instanceof CatchStatement) {
                CatchStatement catchStatement = (CatchStatement) stmInner;
                for (ExceptionGroup.Entry entry : catchStatement.getExceptions()) {
                    blackListed.add(entry.getTryBlockIdentifier());
                }
            }
            // If we're in any blocks which we have left, then we need to backfill.
            Set<BlockIdentifier> blocks = stm.getBlockIdentifiers();
            blocks.removeAll(blackListed);

            for (BlockIdentifier ident : blocks) {
//                if (ident.getBlockType() == BlockType.CASE ||
//                    ident.getBlockType() == BlockType.SWITCH) {
//                    blackListed.add(ident);
//                    continue;
//                }
                if (haveLeft.contains(ident)) {
                    // Backfill, remove from haveLeft.
                    for (int y = x - 1; y >= 0; --y) {
                        Op03SimpleStatement backFill = statements.get(y);
                        if (!backFill.getBlockIdentifiers().add(ident)) break;
                    }
                }
            }
            for (BlockIdentifier wasIn : lastBlocks) {
                if (!blocks.contains(wasIn)) haveLeft.add(wasIn);
            }
            lastBlocks = blocks;
        }
    }
}
