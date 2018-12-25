package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.JumpingStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;

import java.util.List;
import java.util.Set;

public class BreakRewriter {
    public static void rewriteBreakStatements(List<Op03SimpleStatement> statements) {
        Cleaner.reindexInPlace(statements);
        test:
        for (Op03SimpleStatement statement : statements) {
            Statement innerStatement = statement.getStatement();
            if (innerStatement instanceof JumpingStatement) {
                JumpingStatement jumpingStatement = (JumpingStatement) innerStatement;
                //
                // If there's a goto, see if it goes OUT of a known while loop, OR
                // if it goes back to the comparison statement for a known while loop.
                //
                if (jumpingStatement.getJumpType().isUnknown()) {
                    Statement targetInnerStatement = jumpingStatement.getJumpTarget();
                    Op03SimpleStatement targetStatement = (Op03SimpleStatement) targetInnerStatement.getContainer();
                    // TODO : Should we be checking if this is a 'breakable' block?
                    if (targetStatement.getThisComparisonBlock() != null) {
                        BlockType blockType = targetStatement.getThisComparisonBlock().getBlockType();
                        switch (blockType) {
                            default: // hack, figuring out.
                                // Jumps to the comparison test of a WHILE
                                // Continue loopBlock, IF this statement is INSIDE that block.
                                if (BlockIdentifier.blockIsOneOf(targetStatement.getThisComparisonBlock(), statement.getBlockIdentifiers())) {
                                    jumpingStatement.setJumpType(JumpType.CONTINUE);
                                    continue test;
                                }
                        }
                    }
                    if (targetStatement.getBlockStarted() != null &&
                            targetStatement.getBlockStarted().getBlockType() == BlockType.UNCONDITIONALDOLOOP) {
                        if (BlockIdentifier.blockIsOneOf(targetStatement.getBlockStarted(), statement.getBlockIdentifiers())) {
                            jumpingStatement.setJumpType(JumpType.CONTINUE);
                            continue;
                        }
                    }
                    Set<BlockIdentifier> blocksEnded = targetStatement.getBlocksEnded();
                    if (!blocksEnded.isEmpty()) {
                        BlockIdentifier outermostContainedIn = BlockIdentifier.getOutermostContainedIn(blocksEnded, statement.getBlockIdentifiers());
                        // Break to the outermost block.
                        if (outermostContainedIn != null) {
                            jumpingStatement.setJumpType(JumpType.BREAK);
                            continue;
                        }
                    }
                }
            }
        }
    }


}
