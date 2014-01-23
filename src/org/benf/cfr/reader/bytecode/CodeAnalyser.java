package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.SwitchEnumRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.SwitchStringRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.checker.LooseCatchChecker;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.StringBuilderRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredFakeDecompFailure;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableFactory;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.attributes.AttributeCode;
import org.benf.cfr.reader.entities.exceptions.ExceptionAggregator;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageInformationEmpty;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.bytestream.OffsettingByteData;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.LoggerFactory;
import org.benf.cfr.reader.util.output.StdIODumper;

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

    private static final int SHOW_L2_RAW = 1;
    private static final int SHOW_L2_OPS = 2;
    private static final int SHOW_L3_RAW = 3;
    private static final int SHOW_L3_ORDERED = 4;
    private static final int SHOW_L3_CAUGHT = 5;
    private static final int SHOW_L3_JUMPS = 6;
    private static final int SHOW_L3_LOOPS1 = 7;
    private static final int SHOW_L3_EXCEPTION_BLOCKS = 8;
    private static final int SHOW_L4_FINAL_OP3 = 9;

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

    private static class AnalysisResult {
        public DecompilerComments comments;
        public Op04StructuredStatement code;
        public boolean failed;

        private AnalysisResult(DecompilerComments comments, Op04StructuredStatement code, boolean failed) {
            this.comments = comments;
            this.code = code;
            this.failed = failed;
        }

        public boolean hasErrorComment() {
            return comments.hasErrorComment();
        }
    }

    private static final RecoveryOptions recover1 = new RecoveryOptions(
            new RecoveryOption.TrooleanRO(OptionsImpl.FORCE_TOPSORT, Troolean.TRUE, DecompilerComment.AGGRESSIVE_TOPOLOGICAL_SORT),
            new RecoveryOption.BooleanRO(OptionsImpl.LENIENT, Boolean.TRUE),
            new RecoveryOption.TrooleanRO(OptionsImpl.FORCE_RET_PROPAGATE, Troolean.TRUE),
            new RecoveryOption.TrooleanRO(OptionsImpl.FORCE_PRUNE_EXCEPTIONS, Troolean.TRUE, BytecodeMeta.testFlag(BytecodeMeta.CodeInfoFlag.USES_EXCEPTIONS), DecompilerComment.PRUNE_EXCEPTIONS),
            new RecoveryOption.TrooleanRO(OptionsImpl.FORCE_AGGRESSIVE_EXCEPTION_AGG, Troolean.TRUE, BytecodeMeta.testFlag(BytecodeMeta.CodeInfoFlag.USES_EXCEPTIONS))
    );

    private static final RecoveryOptions recover2 = new RecoveryOptions(recover1,
            new RecoveryOption.BooleanRO(OptionsImpl.COMMENT_MONITORS, Boolean.TRUE, BytecodeMeta.testFlag(BytecodeMeta.CodeInfoFlag.USES_MONITORS), DecompilerComment.COMMENT_MONITORS)
    );

    private static final RecoveryOptions[] recoveryOptionsArr = new RecoveryOptions[]{recover1, recover2};

    /*
     * This method should not throw.  If it does, something serious has gone wrong.
     */
    public Op04StructuredStatement getAnalysis(DCCommonState dcCommonState) {
        if (analysed != null) return analysed;

        Options options = dcCommonState.getOptions();

        List<Op01WithProcessedDataAndByteJumps> instrs = getInstrs();
        AnalysisResult res = getAnalysisOrWrapFail(instrs, dcCommonState, options, null);

        if ((res.failed || res.hasErrorComment()) && options.getOption(OptionsImpl.RECOVER)) {
            BytecodeMeta bytecodeMeta = new BytecodeMeta(instrs, originalCodeAttribute);
            for (RecoveryOptions recoveryOptions : recoveryOptionsArr) {
                RecoveryOptions.Applied applied = recoveryOptions.apply(dcCommonState, options, bytecodeMeta);
                if (!applied.valid) continue;
                AnalysisResult nextRes = getAnalysisOrWrapFail(instrs, dcCommonState, applied.options, applied.comments);
                if (nextRes != null) res = nextRes;
                if (res.failed || res.hasErrorComment()) continue;
                break;
            }
        }

        if (res.comments != null) {
            method.setComments(res.comments);
        }

        analysed = res.code;
        return analysed;
    }

    /*
     * This list isn't going to change with recovery passes, so avoid recomputing.
     */
    private List<Op01WithProcessedDataAndByteJumps> getInstrs() {
        ByteData rawCode = originalCodeAttribute.getRawData();
        long codeLength = originalCodeAttribute.getCodeLength();
        ArrayList<Op01WithProcessedDataAndByteJumps> instrs = new ArrayList<Op01WithProcessedDataAndByteJumps>();
        OffsettingByteData bdCode = rawCode.getOffsettingOffsetData(0);
        int offset = 0;

        // We insert a fake NOP right at the start, so that we always know that each operation has a valid
        // parent.  This sentinel assumption is used when inserting try { catch blocks.
        instrs.add(JVMInstr.NOP.createOperation(null, cp, -1));
        do {
            JVMInstr instr = JVMInstr.find(bdCode.getS1At(0));
            Op01WithProcessedDataAndByteJumps oc = instr.createOperation(bdCode, cp, offset);
            int length = oc.getInstructionLength();
            instrs.add(oc);
            offset += length;
            bdCode.advance(length);
        } while (offset < codeLength);
        return instrs;
    }

    private AnalysisResult getAnalysisOrWrapFail(List<Op01WithProcessedDataAndByteJumps> instrs, DCCommonState commonState, Options options, List<DecompilerComment> extraComments) {
        try {
            AnalysisResult res = getAnalysisInner(instrs, commonState, options);
            if (extraComments != null) res.comments.addComments(extraComments);
            return res;
        } catch (RuntimeException e) {
            Op04StructuredStatement coderes = new Op04StructuredStatement(new StructuredFakeDecompFailure(e));
            DecompilerComments comments = new DecompilerComments();
            comments.addComment(new DecompilerComment("Exception decompiling", e));
            return new AnalysisResult(comments, coderes, true);
        }
    }

    /*
     * Note that the options passed in here only apply to this function - don't pass around.
     */
    private AnalysisResult getAnalysisInner(List<Op01WithProcessedDataAndByteJumps> instrs, DCCommonState dcCommonState, Options options) {


        boolean willSort = options.getOption(OptionsImpl.FORCE_TOPSORT) == Troolean.TRUE;

        int showOpsLevel = options.getOption(OptionsImpl.SHOWOPS);

        ClassFile classFile = method.getClassFile();
        ClassFileVersion classFileVersion = classFile.getClassFileVersion();

        DecompilerComments comments = new DecompilerComments();
        Dumper debugDumper = new StdIODumper(new TypeUsageInformationEmpty());
        Map<Integer, Integer> lutByOffset = new HashMap<Integer, Integer>();
        Map<Integer, Integer> lutByIdx = new HashMap<Integer, Integer>();
        int idx2 = 0;
        int offset2 = -1;
        for (Op01WithProcessedDataAndByteJumps op : instrs) {
            lutByOffset.put(offset2, idx2);
            lutByIdx.put(idx2, offset2);
            offset2 += op.getInstructionLength();
            idx2++;
        }
        lutByIdx.put(0, -1);
        lutByOffset.put(-1, 0);

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


        BlockIdentifierFactory blockIdentifierFactory = new BlockIdentifierFactory();

        // These are 'processed' exceptions, which we can use to lay out code.
        ExceptionAggregator exceptions = new ExceptionAggregator(originalCodeAttribute.getExceptionTableEntries(), blockIdentifierFactory, lutByOffset, lutByIdx, instrs, options, cp, method);

//        RawCombinedExceptions rawCombinedExceptions = new RawCombinedExceptions(originalCodeAttribute.getExceptionTableEntries(), blockIdentifierFactory, lutByOffset, lutByIdx, instrs, cp);

        if (showOpsLevel == SHOW_L2_RAW) {
            debugDumper.print("Op2 statements:\n");
            debugDumper.dump(op2list);
            debugDumper.newln().newln();
        }
        //
        // We know the ranges covered by each exception handler - insert try / catch statements around
        // these ranges.
        //
        if (options.getOption(OptionsImpl.FORCE_PRUNE_EXCEPTIONS) == Troolean.TRUE) {
            /*
             * Aggressive exception pruning.  try { x } catch (e) { throw e } , when NOT covered by another exception handler,
             * is a pointless construct.  It also leads to some very badly structured code.
             */
            exceptions.aggressivePruning(lutByOffset, lutByIdx, instrs);
            /*
             * This one's less safe, but...
             */
            exceptions.removeSynchronisedHandlers(lutByOffset, lutByIdx, instrs);
        }

        long codeLength = originalCodeAttribute.getCodeLength();
        op2list = Op02WithProcessedDataAndRefs.insertExceptionBlocks(op2list, exceptions, lutByOffset, cp, codeLength, dcCommonState, options);
        lutByOffset = null; // No longer valid.


        // Populate stack info (each instruction gets references to stack objects
        // consumed / produced.
        // This is the point at which we combine temporaries from merging
        // stacks.
        Op02WithProcessedDataAndRefs.populateStackInfo(op2list, method);

        if (showOpsLevel == SHOW_L2_OPS) {
            debugDumper.print("Op2 statements:\n");
            debugDumper.dump(op2list);
            debugDumper.newln().newln();
        }

        /* Extra fun.  A ret can have a jump back to the instruction immediately following the JSR that called it.
         * So we have to search for RET instructions, then for each of them find any JSRs which could call it, and add
         * a branch from that RET to the JSR.
         *
         * Good news : the JVM mandates that any two paths which reach an instruction should have the same stack depth.
         * This applies to the targets of JSRs too. (though there's nothing stopping the target of the JSR from returning a
         * DIFFERENT stack depth).
         */
        if (Op02WithProcessedDataAndRefs.processJSR(op2list)) {
            // Repopulate stack info, as it will have changed, as we might have cloned instructions.
            Op02WithProcessedDataAndRefs.populateStackInfo(op2list, method);
        }


        // DFS the instructions, unlink any which aren't reachable.
        // This is neccessary because some obfuscated code (and some unobfuscated clojure!!)
        // can generate bytecode with unreachable operations, which confuses later stages which
        // expect all parents of opcodes to have been processed in a DFS.
        Op02WithProcessedDataAndRefs.unlinkUnreachable(op2list);


        // Discover slot re-use, infer invisible constructor parameters, etc.
        Op02WithProcessedDataAndRefs.discoverStorageLiveness(method, comments, op2list);

        // Create a non final version...
        final VariableFactory variableFactory = new VariableFactory(method);

        List<Op03SimpleStatement> op03SimpleParseNodes = Op02WithProcessedDataAndRefs.convertToOp03List(op2list, method, variableFactory, blockIdentifierFactory, dcCommonState);


        if (showOpsLevel == SHOW_L3_RAW) {
            debugDumper.print("Raw Op3 statements:\n");
            for (Op03SimpleStatement node : op03SimpleParseNodes) {
                node.dumpInner(debugDumper);
            }
            debugDumper.print("\n\n");
        }

        if (showOpsLevel == SHOW_L3_ORDERED) {
            debugDumper.newln().newln();
            debugDumper.print("Linked Op3 statements:\n");
            op03SimpleParseNodes.get(0).dump(debugDumper);
            debugDumper.print("\n\n");
        }

        // Expand any 'multiple' statements (eg from dups)
        Op03SimpleStatement.flattenCompoundStatements(op03SimpleParseNodes);

        // Very early, we make a pass through collecting all the method calls for a given type
        // SPECIFICALLY by type pointer, don't alias identical types.
        // We then see if we can infer information from RHS <- LHS re generics, but make sure that we
        // don't do it over aggressievly (see UntypedMapTest);
        Op03SimpleStatement.inferGenericObjectInfoFromCalls(op03SimpleParseNodes);

        // Expand raw switch statements into more useful ones.
        Op03SimpleStatement.replaceRawSwitches(op03SimpleParseNodes, blockIdentifierFactory);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        // Remove 2nd (+) jumps in pointless jump chains.
        Op03SimpleStatement.removePointlessJumps(op03SimpleParseNodes);

        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        Op03SimpleStatement.assignSSAIdentifiers(method, op03SimpleParseNodes);

        // Condense pointless assignments
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);


        // Try to eliminate catch temporaries.
        Op03SimpleStatement.eliminateCatchTemporaries(op03SimpleParseNodes);

        logger.info("identifyCatchBlocks");
        Op03SimpleStatement.identifyCatchBlocks(op03SimpleParseNodes, blockIdentifierFactory);

        Op03SimpleStatement.combineTryCatchBlocks(op03SimpleParseNodes, blockIdentifierFactory);

        if (options.getOption(OptionsImpl.COMMENT_MONITORS)) {
            Op03SimpleStatement.commentMonitors(op03SimpleParseNodes);
        }


        if (showOpsLevel == SHOW_L3_CAUGHT) {
            debugDumper.newln().newln();
            debugDumper.print("After catchblocks.:\n");
            op03SimpleParseNodes.get(0).dump(debugDumper);
        }


        //      Op03SimpleStatement.removePointlessExpressionStatements(op03SimpleParseNodes);

        // Rewrite new / constructor pairs.
        Op03SimpleStatement.condenseConstruction(dcCommonState, method, op03SimpleParseNodes);
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes);
        Op03SimpleStatement.condenseLValueChain1(op03SimpleParseNodes);

        //op03SimpleParseNodes = Op03SimpleStatement.removeRedundantTries(op03SimpleParseNodes);


        Op03SimpleStatement.identifyFinally(options, method, op03SimpleParseNodes, blockIdentifierFactory);

        op03SimpleParseNodes = Op03SimpleStatement.removeUnreachableCode(op03SimpleParseNodes, !willSort);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        /*
         * See if try blocks can be extended with simple returns here.  This is an extra pass, because we might have
         * missed backjumps from catches earlier.
         */
        Op03SimpleStatement.extendTryBlocks(dcCommonState, op03SimpleParseNodes);
        Op03SimpleStatement.combineTryCatchEnds(op03SimpleParseNodes);

        // Remove LValues which are on their own as expressionstatements.
        Op03SimpleStatement.removePointlessExpressionStatements(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.removeUnreachableCode(op03SimpleParseNodes, !willSort);

        // Now we've done our first stage condensation, we want to transform assignments which are
        // self updates into preChanges, if we can.  I.e. x = x | 3  ->  x |= 3,  x = x + 1 -> x+=1 (===++x).
        // (we do this here rather than taking advantage of INC opcodes as this allows us to catch the former)
        logger.info("replacePreChangeAssignments");
        Op03SimpleStatement.replacePreChangeAssignments(op03SimpleParseNodes);

        logger.info("pushPreChangeBack");
        Op03SimpleStatement.pushPreChangeBack(op03SimpleParseNodes);

        Op03SimpleStatement.condenseLValueChain2(op03SimpleParseNodes);

        // Condense again, now we've simplified constructors.
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        if (options.getOption(OptionsImpl.FORCE_TOPSORT) == Troolean.TRUE) {
            Op03SimpleStatement.replaceReturningIfs(op03SimpleParseNodes, true);
            op03SimpleParseNodes = Op03SimpleStatement.removeUnreachableCode(op03SimpleParseNodes, false);
            op03SimpleParseNodes = Op03Blocks.topologicalSort(method, op03SimpleParseNodes);
            Op03SimpleStatement.removePointlessJumps(op03SimpleParseNodes);

            /*
             * Now we've sorted, we need to rebuild switch blocks.....
             */
            Op03SimpleStatement.rebuildSwitches(op03SimpleParseNodes);
            /*
             * This set of operations is /very/ aggressive.
             */
            // This is not neccessarily a sensible thing to do, but we're being aggressive...
            Op03SimpleStatement.rejoinBlocks(op03SimpleParseNodes);
            Op03SimpleStatement.extendTryBlocks(dcCommonState, op03SimpleParseNodes);
            op03SimpleParseNodes = Op03Blocks.combineTryBlocks(method, op03SimpleParseNodes);
            Op03SimpleStatement.combineTryCatchEnds(op03SimpleParseNodes);
            Op03SimpleStatement.rewriteTryBackJumps(op03SimpleParseNodes);
            Op03SimpleStatement.identifyFinally(options, method, op03SimpleParseNodes, blockIdentifierFactory);
            Op03SimpleStatement.replaceReturningIfs(op03SimpleParseNodes, true);
        }

        if (options.getOption(OptionsImpl.FORCE_RET_PROPAGATE) == Troolean.TRUE) {
            Op03SimpleStatement.propagateToReturn(method, op03SimpleParseNodes);
        }

        // At this point, make a first stab at identifying final variables, (or stack values which can't be
        // removed and appear as pseudo-variables).
        Op03SimpleStatement.determineFinal(op03SimpleParseNodes, variableFactory);


        logger.info("sugarAnyonymousArrays");
        Op03SimpleStatement.resugarAnonymousArrays(op03SimpleParseNodes);

        boolean reloop = false;
        do {
            logger.info("collapseAssignmentsIntoConditionals");
            Op03SimpleStatement.collapseAssignmentsIntoConditionals(op03SimpleParseNodes, options);

            // Collapse conditionals into || / &&
            logger.info("condenseConditionals");
            Op03SimpleStatement.condenseConditionals(op03SimpleParseNodes);
            // Condense odder conditionals, which may involve inline ternaries which are
            // hard to work out later.  This isn't going to get everything, but may help!
            reloop = Op03SimpleStatement.condenseConditionals2(op03SimpleParseNodes);

            op03SimpleParseNodes = Op03SimpleStatement.removeUnreachableCode(op03SimpleParseNodes, true);
        } while (reloop);

        logger.info("simplifyConditionals");
        Op03SimpleStatement.simplifyConditionals(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        // Rewrite conditionals which jump into an immediate jump (see specifics)
        logger.info("rewriteNegativeJumps");
        Op03SimpleStatement.rewriteNegativeJumps(op03SimpleParseNodes);

        Op03SimpleStatement.optimiseForTypes(op03SimpleParseNodes);

        if (showOpsLevel == SHOW_L3_JUMPS) {
            debugDumper.newln().newln();
            debugDumper.print("After jumps.:\n");
            op03SimpleParseNodes.get(0).dump(debugDumper);
        }

        // If statements which end up jumping to the final return can really confuse loop detection, so we want
        // to remove them.
        // There is a downside, as this will end up turning a pleasant while (fred) { ... } return
        // into do { if (!fred) return;  ... } while (true).
        //
        // So, we have a pass to eliminate this at the Op04 stage.
        //
        // Before loop detection / sorting, we have a pass to make sure they're not backjumps.

        //
        // If we have been asked to, topologically sort graph.
        // We won't do this unless there's been a problem with normal decompilation strategy.
        //

        if (options.getOption(OptionsImpl.ECLIPSE)) {
            Op03SimpleStatement.eclipseLoopPass(op03SimpleParseNodes);
        }

        // Identify simple while loops.
        logger.info("identifyLoops1");
        Op03SimpleStatement.identifyLoops1(method, op03SimpleParseNodes, blockIdentifierFactory);

        // After we've identified loops, try to push any instructions through a goto
        op03SimpleParseNodes = Op03SimpleStatement.pushThroughGoto(method, op03SimpleParseNodes);

        // Replacing returning ifs early (above, aggressively) interferes with some nice output.
        // Normally we'd do it AFTER loops.
        Op03SimpleStatement.replaceReturningIfs(op03SimpleParseNodes, false);

        if (showOpsLevel == SHOW_L3_LOOPS1) {
            debugDumper.newln().newln();
            debugDumper.print("After loops.:\n");
            op03SimpleParseNodes.get(0).dump(debugDumper);
        }

        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.removeUnreachableCode(op03SimpleParseNodes, true);

        if (showOpsLevel == SHOW_L3_EXCEPTION_BLOCKS) {
            debugDumper.newln().newln();
            debugDumper.print("After exception.:\n");
            op03SimpleParseNodes.get(0).dump(debugDumper);
        }

        // Perform this before simple forward if detection, as it allows us to not have to consider
        // gotos which have been relabelled as continue/break.
        logger.info("rewriteBreakStatements");
        Op03SimpleStatement.rewriteBreakStatements(op03SimpleParseNodes);
        logger.info("rewriteWhilesAsFors");
        Op03SimpleStatement.rewriteDoWhileTruePredAsWhile(op03SimpleParseNodes);
        Op03SimpleStatement.rewriteWhilesAsFors(op03SimpleParseNodes);

        // TODO : I think this is now redundant.
        logger.info("removeSynchronizedCatchBlocks");
        Op03SimpleStatement.removeSynchronizedCatchBlocks(options, op03SimpleParseNodes);

        // identify conditionals which are of the form if (a) { xx } [ else { yy } ]
        // where xx and yy have no GOTOs in them.
        logger.info("identifyNonjumpingConditionals");
        // We need another pass of this to remove jumps which are next to each other except for nops
        op03SimpleParseNodes = Op03SimpleStatement.removeUselessNops(op03SimpleParseNodes);
        Op03SimpleStatement.removePointlessJumps(op03SimpleParseNodes);
        // Identify simple (nested) conditionals - note that this also generates ternary expressions,
        // if the conditional is simple enough.
        Op03SimpleStatement.identifyNonjumpingConditionals(op03SimpleParseNodes, blockIdentifierFactory);
        // Condense again, now we've simplified conditionals, ternaries, etc.
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes);
        if (options.getOption(OptionsImpl.FORCE_RET_PROPAGATE) == Troolean.TRUE) {
            Op03SimpleStatement.propagateToReturn2(method, op03SimpleParseNodes);
        }

        logger.info("removeUselessNops");
        op03SimpleParseNodes = Op03SimpleStatement.removeUselessNops(op03SimpleParseNodes);


        // By now, we've (re)moved several statements, so it's possible that some jumps can be rewritten to
        // breaks again.
        logger.info("removePointlessJumps");
        Op03SimpleStatement.removePointlessJumps(op03SimpleParseNodes);
        logger.info("rewriteBreakStatements");
        Op03SimpleStatement.rewriteBreakStatements(op03SimpleParseNodes);

        // See if we can classify any more gotos - i.e. the last statement in a try block
        // which jumps to immediately after the catch block.
        //
        // While it seems perverse to have another pass at this here, it seems to yield the best results.
        //
        Op03SimpleStatement.classifyGotos(op03SimpleParseNodes);
        //
        // By this point, we've tried to classify ternaries.  We could try pushing some literals
        // very aggressively. (i.e. a=1, if (a) b=1 else b =0; return b. ) -> return 1;
        //
//        Op03SimpleStatement.replaceAssignReturns(op03SimpleParseNodes);
        //
        Op03SimpleStatement.identifyNonjumpingConditionals(op03SimpleParseNodes, blockIdentifierFactory);

        // Introduce java 6 style for (x : array)
        logger.info("rewriteArrayForLoops");
        if (options.getOption(OptionsImpl.ARRAY_ITERATOR, classFileVersion)) {
            Op03SimpleStatement.rewriteArrayForLoops(op03SimpleParseNodes);
        }
        // and for (x : iterable)
        logger.info("rewriteIteratorWhileLoops");
        if (options.getOption(OptionsImpl.COLLECTION_ITERATOR, classFileVersion)) {
            Op03SimpleStatement.rewriteIteratorWhileLoops(op03SimpleParseNodes);
        }

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

        Op03SimpleStatement.rewriteWith(op03SimpleParseNodes, new StringBuilderRewriter(options, classFileVersion));
//        dumper.print("Final Op3 statements:\n");
//        op03SimpleParseNodes.get(0).dump(dumper);


        if (showOpsLevel == SHOW_L4_FINAL_OP3) {
            debugDumper.newln().newln();
            debugDumper.print("Final Op3 statements:\n");
            op03SimpleParseNodes.get(0).dump(debugDumper);
        }
        op03SimpleParseNodes = Op03SimpleStatement.removeUnreachableCode(op03SimpleParseNodes, true);

        Op04StructuredStatement block = Op03SimpleStatement.createInitialStructuredBlock(op03SimpleParseNodes);

        logger.info("tidyTryCatch");
        Op04StructuredStatement.tidyEmptyCatch(block);
        Op04StructuredStatement.tidyTryCatch(block);
        Op04StructuredStatement.inlinePossibles(block);
        Op04StructuredStatement.removeStructuredGotos(block);
        Op04StructuredStatement.removePointlessBlocks(block);
        Op04StructuredStatement.removePointlessReturn(block);
        Op04StructuredStatement.removePrimitiveDeconversion(options, method, block);

        /*
         * If we can't fully structure the code, we bow out here.
         */
        if (!block.isFullyStructured()) {
            comments.addComment(DecompilerComment.UNABLE_TO_STRUCTURE);
            return new AnalysisResult(comments, block, false);
        }
        Op04StructuredStatement.tidyTypedBooleans(block);
        Op04StructuredStatement.prettifyBadLoops(block);

        // Replace with a more generic interface, etc.

        new SwitchStringRewriter(options, classFileVersion).rewrite(block);
        new SwitchEnumRewriter(dcCommonState, classFileVersion).rewrite(block);

        // These should logically be here, but the current versions are better!!
//        new ArrayIterRewriter(cfrState).rewrite(block);
//        new LoopIterRewriter(cfrState).rewrite(block);

        // Now we've got everything nicely block structured, we can have an easier time
        Op04StructuredStatement.discoverVariableScopes(method, block, variableFactory);

        // Done by wholeClass analyser.
//        Op04StructuredStatement.fixInnerClassConstruction(cfrState, method, block);

//        Op04StructuredStatement.inlineSyntheticAccessors(cfrState, method, block);

        if (options.getOption(OptionsImpl.REMOVE_BOILERPLATE)) {
            if (this.method.isConstructor()) Op04StructuredStatement.removeConstructorBoilerplate(block);
        }

        Op04StructuredStatement.rewriteLambdas(dcCommonState, method, block);

        // Some misc translations.
        Op04StructuredStatement.removeUnnecessaryVarargArrays(options, method, block);

        Op04StructuredStatement.removePrimitiveDeconversion(options, method, block);
        // Tidy variable names
        Op04StructuredStatement.tidyVariableNames(method, block);

        /*
         * Now finally run some extra checks to spot wierdness.
         */
        Op04StructuredStatement.applyChecker(new LooseCatchChecker(), block, comments);

        return new AnalysisResult(comments, block, false);
    }


    public void dump(Dumper d) {
        d.newln();
        analysed.dump(d);
    }

}
