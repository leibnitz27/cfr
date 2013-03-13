package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.SwitchEnumRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.SwitchStringRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.StringBuilderRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableFactory;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.attributes.AttributeCode;
import org.benf.cfr.reader.entities.exceptions.ExceptionAggregator;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.bytestream.OffsettingByteData;
import org.benf.cfr.reader.util.getopt.CFRState;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 28/04/2011
 * Time: 07:20
 * To change this template use File | Settings | File Templates.
 */
public class CodeAnalyser {

    private static final int SHOW_L2_OPS = 1;
    private static final int SHOW_L3_RAW = 2;
    private static final int SHOW_L3_ORDERED = 3;

    private final static Logger logger = LoggerFactory.create(CodeAnalyser.class);

    private final AttributeCode originalCodeAttribute;
    private final ConstantPool cp;

    private Method method;

    private Op04StructuredStatement analysed;


    public CodeAnalyser(AttributeCode attributeCode) {
        this.originalCodeAttribute = attributeCode;
        this.cp = attributeCode.getConstantPool();
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Op04StructuredStatement getAnalysis() {

        if (analysed != null) return analysed;

        CFRState cfrState = cp.getCFRState();
        ByteData rawCode = originalCodeAttribute.getRawData();
        long codeLength = originalCodeAttribute.getCodeLength();
        ArrayList<Op01WithProcessedDataAndByteJumps> instrs = new ArrayList<Op01WithProcessedDataAndByteJumps>();
        Map<Integer, Integer> lutByOffset = new HashMap<Integer, Integer>();
        Map<Integer, Integer> lutByIdx = new HashMap<Integer, Integer>();
        OffsettingByteData bdCode = rawCode.getOffsettingOffsetData(0);
        int idx = 1;
        int offset = 0;
        Dumper dumper = new Dumper();

        // We insert a fake NOP right at the start, so that we always know that each operation has a valid
        // parent.  This sentinel assumption is used when inserting try { catch blocks.
        instrs.add(JVMInstr.NOP.createOperation(null, cp, -1));
        lutByIdx.put(0, -1);
        lutByOffset.put(-1, 0);
        do {
            JVMInstr instr = JVMInstr.find(bdCode.getS1At(0));
            Op01WithProcessedDataAndByteJumps oc = instr.createOperation(bdCode, cp, offset);
            int length = oc.getInstructionLength();
            lutByOffset.put(offset, idx);
            lutByIdx.put(idx, offset);
            instrs.add(oc);
            offset += length;
            bdCode.advance(length);
            idx++;
        } while (offset < codeLength);

        List<Op01WithProcessedDataAndByteJumps> op1list = ListFactory.newList();
        List<Op02WithProcessedDataAndRefs> op2list = ListFactory.newList();
        // Now walk the indexed ops
        for (int x = 0; x < instrs.size(); ++x) {
            Op01WithProcessedDataAndByteJumps op1 = instrs.get(x);
            op1list.add(op1);
            Op02WithProcessedDataAndRefs op2 = op1.createOp2(cp, x);
            op2list.add(op2);
        }


        for (int x = 0; x < instrs.size(); ++x) {
            int offsetOfThisInstruction = lutByIdx.get(x);
            int[] targetIdxs = op1list.get(x).getAbsoluteIndexJumps(offsetOfThisInstruction, lutByOffset);
            Op02WithProcessedDataAndRefs source = op2list.get(x);
            for (int targetIdx : targetIdxs) {
                Op02WithProcessedDataAndRefs target = op2list.get(targetIdx);
                source.addTarget(target);
                target.addSource(source);
            }
        }

        /* Extra fun.  A ret can have a jump back to the instruction immediately following the JSR that called it.
         * So we have to search for RET instructions, then for each of them find any JSRs which could call it, and add
         * a branch from that RET to the JSR.
         *
         * Good news : the JVM mandates that any two paths which reach an instruction should have the same stack depth.
         * This applies to the targets of JSRs too. (though there's nothing stopping the target of the JSR from returning a
         * DIFFERENT stack depth).
         */
        Op02WithProcessedDataAndRefs.linkRetsToJSR(op2list);

        BlockIdentifierFactory blockIdentifierFactory = new BlockIdentifierFactory();
        ExceptionAggregator exceptions = new ExceptionAggregator(originalCodeAttribute.getExceptionTableEntries(), blockIdentifierFactory, cp);
        //
        // We know the ranges covered by each exception handler - insert try / catch statements around
        // these ranges.
        //

        op2list = Op02WithProcessedDataAndRefs.insertExceptionBlocks(op2list, exceptions, lutByOffset, cp, codeLength);
        lutByOffset = null; // No longer valid.


        // Populate stack info (each instruction gets references to stack objects
        // consumed / produced.
        // This is the point at which we combine temporaries from merging
        // stacks.
        Op02WithProcessedDataAndRefs.populateStackInfo(op2list);

        if (cfrState.getShowOps() == SHOW_L2_OPS) {
            dumper.print("Op2 statements:\n");
            dumper.dump(op2list);
            dumper.newln().newln();
        }

        // DFS the instructions, unlink any which aren't reachable.
        // This is neccessary because some obfuscated code (and some unobfuscated clojure!!)
        // can generate bytecode with unreachable operations, which confuses later stages which
        // expect all parents of opcodes to have been processed in a DFS.
        Op02WithProcessedDataAndRefs.unlinkUnreachable(op2list);

        // Create a non final version...
        final VariableFactory variableFactory = new VariableFactory(method);
        final ClassFile classFile = method.getClassFile();
        List<Op03SimpleStatement> op03SimpleParseNodes = Op02WithProcessedDataAndRefs.convertToOp03List(op2list, classFile, variableFactory, blockIdentifierFactory);


        if (cfrState.getShowOps() == SHOW_L3_RAW) {
            dumper.print("Raw Op3 statements:\n");
            for (Op03SimpleStatement node : op03SimpleParseNodes) {
                node.dumpInner(dumper);
            }
        }

        if (cfrState.getShowOps() == SHOW_L3_ORDERED) {
            dumper.newln().newln();
            dumper.print("Linked Op3 statements:\n");
            op03SimpleParseNodes.get(0).dump(dumper);
        }

        // Expand any 'multiple' statements (eg from dups)
        Op03SimpleStatement.flattenCompoundStatements(op03SimpleParseNodes);

        // Expand raw switch statements into more useful ones.
        Op03SimpleStatement.replaceRawSwitches(op03SimpleParseNodes, blockIdentifierFactory);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        // Remove 2nd (+) jumps in pointless jump chains.
        Op03SimpleStatement.removePointlessJumps(op03SimpleParseNodes);

        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        Op03SimpleStatement.assignSSAIdentifiers(op03SimpleParseNodes);

        // Condense pointless assignments
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);


        // Try to eliminate catch temporaries.
        Op03SimpleStatement.eliminateCatchTemporaries(op03SimpleParseNodes);

        // Rewrite new / constructor pairs.
        Op03SimpleStatement.condenseConstruction(op03SimpleParseNodes);
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        // Now we've done our first stage condensation, we want to transform assignments which are
        // self updates into preChanges, if we can.  I.e. x = x | 3  ->  x |= 3,  x = x + 1 -> x+=1 (===++x).
        // (we do this here rather than taking advantage of INC opcodes as this allows us to catch the former)
        logger.info("replacePreChangeAssignments");
        Op03SimpleStatement.replacePreChangeAssignments(op03SimpleParseNodes);

        logger.info("pushPreChangeBack");
        Op03SimpleStatement.pushPreChangeBack(op03SimpleParseNodes);

        // Condense again, now we've simplified constructors.
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        logger.info("sugarAnyonymousArrays");
        Op03SimpleStatement.resugarAnonymousArrays(op03SimpleParseNodes);

        logger.info("collapseAssignmentsIntoConditionals");
        Op03SimpleStatement.collapseAssignmentsIntoConditionals(op03SimpleParseNodes);

        // Collapse conditionals into || / &&
        logger.info("condenseConditionals");
        Op03SimpleStatement.condenseConditionals(op03SimpleParseNodes);
        logger.info("simplifyConditionals");
        Op03SimpleStatement.simplifyConditionals(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        // Rewrite conditionals which jump into an immediate jump (see specifics)
        logger.info("rewriteNegativeJumps");
        Op03SimpleStatement.rewriteNegativeJumps(op03SimpleParseNodes);

        Op03SimpleStatement.optimiseForTypes(op03SimpleParseNodes);


        // Identify simple while loops.
        logger.info("identifyLoops1");
        Op03SimpleStatement.identifyLoops1(op03SimpleParseNodes, blockIdentifierFactory);

        // Perform this before simple forward if detection, as it allows us to not have to consider
        // gotos which have been relabelled as continue/break.
        logger.info("rewriteBreakStatements");
        Op03SimpleStatement.rewriteBreakStatements(op03SimpleParseNodes);
        logger.info("rewriteWhilesAsFors");
        Op03SimpleStatement.rewriteWhilesAsFors(op03SimpleParseNodes);

        logger.info("identifyCatchBlocks");
        Op03SimpleStatement.identifyCatchBlocks(op03SimpleParseNodes, blockIdentifierFactory);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        logger.info("removeSynchronizedCatchBlocks");
        Op03SimpleStatement.removeSynchronizedCatchBlocks(op03SimpleParseNodes);

        // identify conditionals which are of the form if (a) { xx } [ else { yy } ]
        // where xx and yy have no GOTOs in them.
        logger.info("identifyNonjumpingConditionals");
        // We need another pass of this to remove jumps which are next to each other except for nops
        op03SimpleParseNodes = Op03SimpleStatement.removeUselessNops(op03SimpleParseNodes);
        Op03SimpleStatement.removePointlessJumps(op03SimpleParseNodes);
        Op03SimpleStatement.identifyNonjumpingConditionals(op03SimpleParseNodes, blockIdentifierFactory);

        logger.info("removeUselessNops");
        op03SimpleParseNodes = Op03SimpleStatement.removeUselessNops(op03SimpleParseNodes);


        // By now, we've (re)moved several statements, so it's possible that some jumps can be rewritten to
        // breaks again.
        logger.info("removePointlessJumps");
        Op03SimpleStatement.removePointlessJumps(op03SimpleParseNodes);
        logger.info("rewriteBreakStatements");
        Op03SimpleStatement.rewriteBreakStatements(op03SimpleParseNodes);

        // Introduce java 6 style for (x : array)
        logger.info("rewriteArrayForLoops");
        if (cfrState.getBooleanOpt(CFRState.ARRAY_ITERATOR))
            Op03SimpleStatement.rewriteArrayForLoops(op03SimpleParseNodes);
        // and for (x : iterable)
        logger.info("rewriteIteratorWhileLoops");
        if (cfrState.getBooleanOpt(CFRState.COLLECTION_ITERATOR))
            Op03SimpleStatement.rewriteIteratorWhileLoops(op03SimpleParseNodes);

        logger.info("findSynchronizedBlocks");
        Op03SimpleStatement.findSynchronizedBlocks(op03SimpleParseNodes);

        logger.info("removePointlessSwitchDefaults");
        Op03SimpleStatement.removePointlessSwitchDefaults(op03SimpleParseNodes);


//        dumper.print("Raw Op3 statements:\n");
//        op03SimpleParseNodes.get(0).dump(dumper);

        logger.info("removeUselessNops");
        op03SimpleParseNodes = Op03SimpleStatement.removeUselessNops(op03SimpleParseNodes);
//
//        dumper.print("Raw Op3 statements:\n");
//        for (Op03SimpleStatement node : op03SimpleParseNodes) {
//            node.dumpInner(dumper);
//        }

        Op03SimpleStatement.rewriteWith(op03SimpleParseNodes, new StringBuilderRewriter());
//        dumper.print("Final Op3 statements:\n");
//        op03SimpleParseNodes.get(0).dump(dumper);


        Op04StructuredStatement block = Op03SimpleStatement.createInitialStructuredBlock(op03SimpleParseNodes);

        logger.info("tidyTryCatch");
        Op04StructuredStatement.tidyTryCatch(block);
        Op04StructuredStatement.removePointlessReturn(block);

        // Replace with a more generic interface, etc.

        new SwitchStringRewriter(cfrState).rewrite(block);
        new SwitchEnumRewriter(cfrState).rewrite(block);

        if (block.isFullyStructured()) {
            // Now we've got everything nicely block structured, we can have an easier time
            Op04StructuredStatement.discoverVariableScopes(block);
        }

        this.analysed = block;
        return analysed;
    }


    public void dump(Dumper d) {
        d.newln();
        analysed.dump(d);
    }

}
