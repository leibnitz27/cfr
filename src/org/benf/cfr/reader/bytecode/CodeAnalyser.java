package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op2rewriters.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.SwitchEnumRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.SwitchStringRewriter;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.checker.LooseCatchChecker;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExplicitTypeCallRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.StringBuilderRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.XorRewriter;
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
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;
import org.benf.cfr.reader.util.output.LoggerFactory;
import org.benf.cfr.reader.util.output.StdIODumper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

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
        public AnonymousClassUsage anonymousClassUsage;
        public boolean failed;
        public boolean exception;

        private AnalysisResult(DecompilerComments comments, Op04StructuredStatement code, AnonymousClassUsage anonymousClassUsage) {
            this.anonymousClassUsage = anonymousClassUsage;
            this.comments = comments;
            this.code = code;
            boolean failed = false;
            boolean exception = false;
            for (DecompilerComment comment : comments.getCommentCollection()) {
                if (comment.isFailed()) {
                    failed = true;
                }
                if (comment.isException()) {
                    exception = true;
                }
            }
            this.failed = failed;
            this.exception = exception;
        }
    }

    private static final RecoveryOptions recover0 = new RecoveryOptions(
            new RecoveryOption.TrooleanRO(OptionsImpl.RECOVER_TYPECLASHES, Troolean.TRUE, BytecodeMeta.hasAnyFlag(BytecodeMeta.CodeInfoFlag.LIVENESS_CLASH)),
            new RecoveryOption.TrooleanRO(OptionsImpl.USE_RECOVERED_ITERATOR_TYPE_HINTS, Troolean.TRUE, BytecodeMeta.hasAnyFlag(BytecodeMeta.CodeInfoFlag.ITERATED_TYPE_HINTS)),
            new RecoveryOption.BooleanRO(OptionsImpl.STATIC_INIT_RETURN, Boolean.FALSE)
    );

    private static final RecoveryOptions recover0a = new RecoveryOptions(recover0,
            new RecoveryOption.TrooleanRO(OptionsImpl.FORCE_COND_PROPAGATE, Troolean.TRUE, DecompilerComment.COND_PROPAGATE),
            new RecoveryOption.TrooleanRO(OptionsImpl.FORCE_RETURNING_IFS, Troolean.TRUE, DecompilerComment.RETURNING_IFS)
    );

    private static final RecoveryOptions recover1 = new RecoveryOptions(recover0,
            new RecoveryOption.TrooleanRO(OptionsImpl.FORCE_TOPSORT, Troolean.TRUE, DecompilerComment.AGGRESSIVE_TOPOLOGICAL_SORT),
            new RecoveryOption.TrooleanRO(OptionsImpl.FOR_LOOP_CAPTURE, Troolean.TRUE),
            new RecoveryOption.BooleanRO(OptionsImpl.LENIENT, Boolean.TRUE),
            new RecoveryOption.TrooleanRO(OptionsImpl.FORCE_COND_PROPAGATE, Troolean.TRUE),
            new RecoveryOption.TrooleanRO(OptionsImpl.FORCE_PRUNE_EXCEPTIONS, Troolean.TRUE, BytecodeMeta.hasAnyFlag(BytecodeMeta.CodeInfoFlag.USES_EXCEPTIONS), DecompilerComment.PRUNE_EXCEPTIONS),
            new RecoveryOption.TrooleanRO(OptionsImpl.FORCE_AGGRESSIVE_EXCEPTION_AGG, Troolean.TRUE, BytecodeMeta.hasAnyFlag(BytecodeMeta.CodeInfoFlag.USES_EXCEPTIONS))
    );

    private static final RecoveryOptions recover2 = new RecoveryOptions(recover1,
            new RecoveryOption.TrooleanRO(OptionsImpl.FORCE_TOPSORT_EXTRA, Troolean.TRUE)
    );

    private static final RecoveryOptions recover3 = new RecoveryOptions(recover1,
            new RecoveryOption.BooleanRO(OptionsImpl.COMMENT_MONITORS, Boolean.TRUE, BytecodeMeta.hasAnyFlag(BytecodeMeta.CodeInfoFlag.USES_MONITORS), DecompilerComment.COMMENT_MONITORS),
            new RecoveryOption.TrooleanRO(OptionsImpl.FORCE_RETURNING_IFS, Troolean.TRUE, DecompilerComment.RETURNING_IFS)
    );

    private static final RecoveryOptions[] recoveryOptionsArr = new RecoveryOptions[]{recover0, recover0a, recover1, recover2, recover3};

    /*
     * This method should not throw.  If it does, something serious has gone wrong.
     */
    public Op04StructuredStatement getAnalysis(DCCommonState dcCommonState) {
        if (analysed != null) return analysed;

        Options options = dcCommonState.getOptions();
        List<Op01WithProcessedDataAndByteJumps> instrs = getInstrs();

        AnalysisResult res = null;

        /*
         * Very quick scan to check for presence of certain instructions.
         */
        BytecodeMeta bytecodeMeta = new BytecodeMeta(instrs, originalCodeAttribute);

        if (options.optionIsSet(OptionsImpl.FORCE_PASS)) {
            int pass = options.getOption(OptionsImpl.FORCE_PASS);
            if (pass < 0 || pass >= recoveryOptionsArr.length) {
                throw new IllegalArgumentException("Illegal recovery pass idx");
            }
            RecoveryOptions.Applied applied = recoveryOptionsArr[pass].apply(dcCommonState, options, bytecodeMeta);
            res = getAnalysisOrWrapFail(pass, instrs, dcCommonState, applied.options, applied.comments, bytecodeMeta);
        } else {

            res = getAnalysisOrWrapFail(0, instrs, dcCommonState, options, null, bytecodeMeta);

            if (res.failed && options.getOption(OptionsImpl.RECOVER)) {
                int passIdx = 1;
                for (RecoveryOptions recoveryOptions : recoveryOptionsArr) {
                    RecoveryOptions.Applied applied = recoveryOptions.apply(dcCommonState, options, bytecodeMeta);
                    if (!applied.valid) continue;
                    AnalysisResult nextRes = getAnalysisOrWrapFail(passIdx++, instrs, dcCommonState, applied.options, applied.comments, bytecodeMeta);
                    if (nextRes != null) {
                        if (res.failed && nextRes.failed) {
                            // If they both failed, only replace if the later failure is not an exception.
                            // (or if the earlier one is).
                            if (res.exception || !nextRes.exception) res = nextRes;
                        } else {
                            res = nextRes;
                        }
                    }
                    if (res.failed) continue;
                    break;
                }
            }
        }

        if (res.comments != null) {
            method.setComments(res.comments);
        }

        /*
         * Take the anonymous usages from the selected result.
         */
        res.anonymousClassUsage.useNotes();

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

    private AnalysisResult getAnalysisOrWrapFail(int passIdx, List<Op01WithProcessedDataAndByteJumps> instrs, DCCommonState commonState, Options options, List<DecompilerComment> extraComments, BytecodeMeta bytecodeMeta) {
        try {
            AnalysisResult res = getAnalysisInner(instrs, commonState, options, bytecodeMeta, passIdx);
            if (extraComments != null) res.comments.addComments(extraComments);
            return res;
        } catch (RuntimeException e) {
            Op04StructuredStatement coderes = new Op04StructuredStatement(new StructuredFakeDecompFailure(e));
            DecompilerComments comments = new DecompilerComments();
            comments.addComment(new DecompilerComment("Exception decompiling", e));
            return new AnalysisResult(comments, coderes, new AnonymousClassUsage());
        }
    }

    /*
     * Note that the options passed in here only apply to this function - don't pass around.
     *
     * passIdx is only useful for breakpointing.
     */
    private AnalysisResult getAnalysisInner(List<Op01WithProcessedDataAndByteJumps> instrs, DCCommonState dcCommonState, Options options, BytecodeMeta bytecodeMeta, int passIdx) {


        boolean willSort = options.getOption(OptionsImpl.FORCE_TOPSORT) == Troolean.TRUE;

        int showOpsLevel = options.getOption(OptionsImpl.SHOWOPS);

        ClassFile classFile = method.getClassFile();
        ClassFileVersion classFileVersion = classFile.getClassFileVersion();

        DecompilerComments comments = new DecompilerComments();

        boolean aggressiveSizeReductions = options.getOption(OptionsImpl.AGGRESSIVE_SIZE_REDUCTION_THRESHOLD) < instrs.size();
        if (aggressiveSizeReductions) {
            comments.addComment("Opcode count of " + instrs.size() + " triggered aggressive code reduction.  Override with --" + OptionsImpl.AGGRESSIVE_SIZE_REDUCTION_THRESHOLD.getName() + ".");
        }

        Dumper debugDumper = new StdIODumper(new TypeUsageInformationEmpty(), options, new IllegalIdentifierDump.Nop());
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


        for (int x = 0, len = instrs.size(); x < len; ++x) {
            int offsetOfThisInstruction = lutByIdx.get(x);
            int[] targetIdxs = op1list.get(x).getAbsoluteIndexJumps(offsetOfThisInstruction, lutByOffset);
            Op02WithProcessedDataAndRefs source = op2list.get(x);
            for (int targetIdx : targetIdxs) {
                if (targetIdx < len) {
                    Op02WithProcessedDataAndRefs target = op2list.get(targetIdx);
                    source.addTarget(target);
                    target.addSource(source);
                }
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

        /*
         * If we're dealing with lambdas, remove class file getter.
         *
         * We need to nop out relevant instructions ASAP, so as not to introduce pointless
         * temporaries.
         */
        if (options.getOption(OptionsImpl.REWRITE_LAMBDAS, classFileVersion) &&
                bytecodeMeta.has(BytecodeMeta.CodeInfoFlag.USES_INVOKEDYNAMIC)) {
            Op02GetClassRewriter.removeInvokeGetClass(classFile, op2list, GetClassTestLambda.INSTANCE);
        }
        Op02GetClassRewriter.removeInvokeGetClass(classFile, op2list, GetClassTestInnerConstructor.INSTANCE);

        long codeLength = originalCodeAttribute.getCodeLength();
        op2list = Op02WithProcessedDataAndRefs.insertExceptionBlocks(op2list, exceptions, lutByOffset, cp, codeLength, dcCommonState, options);
        lutByOffset = null; // No longer valid.

        /*
         * Now we know what's covered by exceptions, we can see if we can remove intermediate stores, which significantly complicate
         * SSA analysis.
         *
         * Note - this will ONLY be a valid transformation in the absence of exceptions / branching.
         */
        if (aggressiveSizeReductions) {
            Op02RedundantStoreRewriter.rewrite(op2list, originalCodeAttribute.getMaxLocals());
        }


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
        Op02WithProcessedDataAndRefs.discoverStorageLiveness(method, comments, op2list, bytecodeMeta, options);

        // Create a non final version...
        final VariableFactory variableFactory = new VariableFactory(method);

        TypeHintRecovery typeHintRecovery = options.optionIsSet(OptionsImpl.USE_RECOVERED_ITERATOR_TYPE_HINTS) ?
                new TypeHintRecoveryImpl(bytecodeMeta) : TypeHintRecoveryNone.INSTANCE;

        List<Op03SimpleStatement> op03SimpleParseNodes = Op02WithProcessedDataAndRefs.convertToOp03List(op2list, method, variableFactory, blockIdentifierFactory, dcCommonState, typeHintRecovery);
        // Renumber, just in case JSR stage (or something) has left bad labellings.
        op03SimpleParseNodes = Cleaner.sortAndRenumber(op03SimpleParseNodes);


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
        Misc.flattenCompoundStatements(op03SimpleParseNodes);
        // Before we get complicated, see if there are any values which have been left with null/void types, but have
        // known base information which can improve it.
        Op03SimpleStatement.rewriteWith(op03SimpleParseNodes, new NullTypedLValueRewriter());

        // Very early, we make a pass through collecting all the method calls for a given type
        // SPECIFICALLY by type pointer, don't alias identical types.
        // We then see if we can infer information from RHS <- LHS re generics, but make sure that we
        // don't do it over aggressively (see UntypedMapTest);
        GenericInferer.inferGenericObjectInfoFromCalls(op03SimpleParseNodes);

        // Expand raw switch statements into more useful ones.
        SwitchReplacer.replaceRawSwitches(method, op03SimpleParseNodes, blockIdentifierFactory);
        op03SimpleParseNodes = Cleaner.sortAndRenumber(op03SimpleParseNodes);

        // Remove 2nd (+) jumps in pointless jump chains.
        Op03SimpleStatement.removePointlessJumps(op03SimpleParseNodes);

        op03SimpleParseNodes = Cleaner.sortAndRenumber(op03SimpleParseNodes);

        if (aggressiveSizeReductions) {
            op03SimpleParseNodes = LValuePropSimple.condenseSimpleLValues(op03SimpleParseNodes);
        }

        Op03SimpleStatement.assignSSAIdentifiers(method, op03SimpleParseNodes);

        // Condense pointless assignments
        LValueProp.condenseLValues(op03SimpleParseNodes);
        op03SimpleParseNodes = Cleaner.sortAndRenumber(op03SimpleParseNodes);


        // Try to eliminate catch temporaries.
        op03SimpleParseNodes = Op03SimpleStatement.eliminateCatchTemporaries(op03SimpleParseNodes);

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
        AnonymousClassUsage anonymousClassUsage = new AnonymousClassUsage();
        Op03SimpleStatement.condenseConstruction(dcCommonState, method, op03SimpleParseNodes, anonymousClassUsage);
        LValueProp.condenseLValues(op03SimpleParseNodes);
        Op03SimpleStatement.condenseLValueChain1(op03SimpleParseNodes);

        op03SimpleParseNodes = StaticInitReturnRewriter.rewrite(options, method, op03SimpleParseNodes);

        op03SimpleParseNodes = Op03SimpleStatement.removeRedundantTries(op03SimpleParseNodes);

        FinallyRewriter.identifyFinally(options, method, op03SimpleParseNodes, blockIdentifierFactory);

        op03SimpleParseNodes = Cleaner.removeUnreachableCode(op03SimpleParseNodes, !willSort);
        op03SimpleParseNodes = Cleaner.sortAndRenumber(op03SimpleParseNodes);

        /*
         * See if try blocks can be extended with simple returns here.  This is an extra pass, because we might have
         * missed backjumps from catches earlier.
         */
        Op03SimpleStatement.extendTryBlocks(dcCommonState, op03SimpleParseNodes);
        Op03SimpleStatement.combineTryCatchEnds(op03SimpleParseNodes);

        // Remove LValues which are on their own as expressionstatements.
        Op03SimpleStatement.removePointlessExpressionStatements(op03SimpleParseNodes);
        op03SimpleParseNodes = Cleaner.removeUnreachableCode(op03SimpleParseNodes, !willSort);

        // Now we've done our first stage condensation, we want to transform assignments which are
        // self updates into preChanges, if we can.  I.e. x = x | 3  ->  x |= 3,  x = x + 1 -> x+=1 (===++x).
        // (we do this here rather than taking advantage of INC opcodes as this allows us to catch the former)
        Op03SimpleStatement.replacePrePostChangeAssignments(op03SimpleParseNodes);

        // Some pre-changes can be converted into post-changes.
        Op03SimpleStatement.pushPreChangeBack(op03SimpleParseNodes);

        Op03SimpleStatement.condenseLValueChain2(op03SimpleParseNodes);

        // Condense again, now we've simplified constructors.
        // Inline assingments need to be dealt with HERE (!).
//        LValueProp.condenseLValues(op03SimpleParseNodes);
        Op03SimpleStatement.collapseAssignmentsIntoConditionals(op03SimpleParseNodes, options);
        LValueProp.condenseLValues(op03SimpleParseNodes);
        op03SimpleParseNodes = Cleaner.sortAndRenumber(op03SimpleParseNodes);

        if (options.getOption(OptionsImpl.FORCE_COND_PROPAGATE) == Troolean.TRUE) {
            RemoveDeterministicJumps.apply(method, op03SimpleParseNodes);
        }

        if (options.getOption(OptionsImpl.FORCE_TOPSORT) == Troolean.TRUE) {
            if (options.getOption(OptionsImpl.FORCE_RETURNING_IFS) == Troolean.TRUE) {
                Op03SimpleStatement.replaceReturningIfs(op03SimpleParseNodes, true);
            }
            if (options.getOption(OptionsImpl.FORCE_COND_PROPAGATE) == Troolean.TRUE) {
                Op03SimpleStatement.propagateToReturn2(method, op03SimpleParseNodes);
            }

            op03SimpleParseNodes = Cleaner.removeUnreachableCode(op03SimpleParseNodes, false);
//            Op03SimpleStatement.extendCatchBlocks(op03SimpleParseNodes);

            op03SimpleParseNodes = Op03Blocks.topologicalSort(method, op03SimpleParseNodes, comments, options);
            Op03SimpleStatement.removePointlessJumps(op03SimpleParseNodes);

            /*
             * Now we've sorted, we need to rebuild switch blocks.....
             */
            SwitchReplacer.rebuildSwitches(op03SimpleParseNodes);
            /*
             * This set of operations is /very/ aggressive.
             */
            // This is not neccessarily a sensible thing to do, but we're being aggressive...
            Op03SimpleStatement.rejoinBlocks(op03SimpleParseNodes);
            Op03SimpleStatement.extendTryBlocks(dcCommonState, op03SimpleParseNodes);
            op03SimpleParseNodes = Op03Blocks.combineTryBlocks(method, op03SimpleParseNodes);
            Op03SimpleStatement.combineTryCatchEnds(op03SimpleParseNodes);
            Op03SimpleStatement.rewriteTryBackJumps(op03SimpleParseNodes);
            FinallyRewriter.identifyFinally(options, method, op03SimpleParseNodes, blockIdentifierFactory);
            if (options.getOption(OptionsImpl.FORCE_RETURNING_IFS) == Troolean.TRUE) {
                Op03SimpleStatement.replaceReturningIfs(op03SimpleParseNodes, true);
            }
        }

        /*
         * Push constants through conditionals to returns ( move the returns back )- i.e. if the value we're
         * returning is deterministic, return that.
         */
        if (options.getOption(OptionsImpl.FORCE_COND_PROPAGATE) == Troolean.TRUE) {
            RemoveDeterministicJumps.propagateToReturn(method, op03SimpleParseNodes);
        }

        logger.info("sugarAnyonymousArrays");
        AnonymousArray.resugarAnonymousArrays(op03SimpleParseNodes);

        boolean reloop = false;
        do {
            Op03SimpleStatement.rewriteNegativeJumps(op03SimpleParseNodes, true);

            logger.info("collapseAssignmentsIntoConditionals");
            Op03SimpleStatement.collapseAssignmentsIntoConditionals(op03SimpleParseNodes, options);

            // Collapse conditionals into || / &&
            logger.info("condenseConditionals");
            reloop = Op03SimpleStatement.condenseConditionals(op03SimpleParseNodes);
            // Condense odder conditionals, which may involve inline ternaries which are
            // hard to work out later.  This isn't going to get everything, but may help!
            //
            reloop = reloop | Op03SimpleStatement.condenseConditionals2(op03SimpleParseNodes);
            reloop = reloop | Op03SimpleStatement.normalizeDupAssigns(op03SimpleParseNodes);
            if (reloop) {
                LValueProp.condenseLValues(op03SimpleParseNodes);
            }
            op03SimpleParseNodes = Cleaner.removeUnreachableCode(op03SimpleParseNodes, true);

        } while (reloop);

        logger.info("simplifyConditionals");
        Op03SimpleStatement.simplifyConditionals(op03SimpleParseNodes, false);
        op03SimpleParseNodes = Cleaner.sortAndRenumber(op03SimpleParseNodes);

        // Rewrite conditionals which jump into an immediate jump (see specifics)
        logger.info("rewriteNegativeJumps");
        Op03SimpleStatement.rewriteNegativeJumps(op03SimpleParseNodes, false);

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
      //  Op03SimpleStatement.classifyGotos(op03SimpleParseNodes);

        // Identify simple while loops.
        logger.info("identifyLoops1");
        op03SimpleParseNodes = Cleaner.removeUnreachableCode(op03SimpleParseNodes, true);
        LoopIdentifier.identifyLoops1(method, op03SimpleParseNodes, blockIdentifierFactory);

        // After we've identified loops, try to push any instructions through a goto
        op03SimpleParseNodes = Op03SimpleStatement.pushThroughGoto(method, op03SimpleParseNodes);

        // Replacing returning ifs early (above, aggressively) interferes with some nice output.
        // Normally we'd do it AFTER loops.
        if (options.getOption(OptionsImpl.FORCE_RETURNING_IFS) == Troolean.TRUE) {
            Op03SimpleStatement.replaceReturningIfs(op03SimpleParseNodes, false);
        }

        if (showOpsLevel == SHOW_L3_LOOPS1) {
            debugDumper.newln().newln();
            debugDumper.print("After loops.:\n");
            op03SimpleParseNodes.get(0).dump(debugDumper);
        }

        op03SimpleParseNodes = Cleaner.sortAndRenumber(op03SimpleParseNodes);
        op03SimpleParseNodes = Cleaner.removeUnreachableCode(op03SimpleParseNodes, true);

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
        Op03SimpleStatement.rewriteWhilesAsFors(options, op03SimpleParseNodes);

        // TODO : I think this is now redundant.
        logger.info("removeSynchronizedCatchBlocks");
        Op03SimpleStatement.removeSynchronizedCatchBlocks(options, op03SimpleParseNodes);

        // identify conditionals which are of the form if (a) { xx } [ else { yy } ]
        // where xx and yy have no GOTOs in them.
        logger.info("identifyNonjumpingConditionals");
        // We need another pass of this to remove jumps which are next to each other except for nops
        op03SimpleParseNodes = Op03SimpleStatement.removeUselessNops(op03SimpleParseNodes);
        Op03SimpleStatement.removePointlessJumps(op03SimpleParseNodes);
        // BUT....
        // After we've removed pointless jumps, let's possibly re-add them, so that the structure of
        // try blocks doesn't end up with confusing jumps.  See ExceptionTest11.
        // (this removal and re-adding may seem daft, (and it often is), but we normalise code
        // and handle more cases by doing it).
        Op03SimpleStatement.extractExceptionJumps(op03SimpleParseNodes);

        // Identify simple (nested) conditionals - note that this also generates ternary expressions,
        // if the conditional is simple enough.
        ConditionalRewriter.identifyNonjumpingConditionals(op03SimpleParseNodes, blockIdentifierFactory);
        // Condense again, now we've simplified conditionals, ternaries, etc.
        LValueProp.condenseLValues(op03SimpleParseNodes);
        if (options.getOption(OptionsImpl.FORCE_COND_PROPAGATE) == Troolean.TRUE) {
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
        if (options.getOption(OptionsImpl.LABELLED_BLOCKS)) {
            Op03SimpleStatement.classifyAnonymousBlockGotos(op03SimpleParseNodes);
        }
        //
        // By this point, we've tried to classify ternaries.  We could try pushing some literals
        // very aggressively. (i.e. a=1, if (a) b=1 else b =0; return b. ) -> return 1;
        //
//        Op03SimpleStatement.replaceAssignReturns(op03SimpleParseNodes);
        //
        ConditionalRewriter.identifyNonjumpingConditionals(op03SimpleParseNodes, blockIdentifierFactory);

        /*
         * Now we've got here, there's no benefit in having spurious inline assignments.  Where possible,
         * pull them out!
         */
        InlineDeAssigner.extractAssignments(op03SimpleParseNodes);

        // Introduce java 6 style for (x : array)
        logger.info("rewriteArrayForLoops");
        boolean checkLoopTypeClash = false;
        if (options.getOption(OptionsImpl.ARRAY_ITERATOR, classFileVersion)) {
            IterLoopRewriter.rewriteArrayForLoops(op03SimpleParseNodes);
            checkLoopTypeClash = true;
        }
        // and for (x : iterable)
        logger.info("rewriteIteratorWhileLoops");
        if (options.getOption(OptionsImpl.COLLECTION_ITERATOR, classFileVersion)) {
            IterLoopRewriter.rewriteIteratorWhileLoops(op03SimpleParseNodes);
            checkLoopTypeClash = true;
        }

        logger.info("findSynchronizedBlocks");
        SynchronizedBlocks.findSynchronizedBlocks(op03SimpleParseNodes);

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
        Op03SimpleStatement.rewriteWith(op03SimpleParseNodes, new XorRewriter());
//        dumper.print("Final Op3 statements:\n");
//        op03SimpleParseNodes.get(0).dump(dumper);


        if (showOpsLevel == SHOW_L4_FINAL_OP3) {
            debugDumper.newln().newln();
            debugDumper.print("Final Op3 statements:\n");
            op03SimpleParseNodes.get(0).dump(debugDumper);
        }
        op03SimpleParseNodes = Cleaner.removeUnreachableCode(op03SimpleParseNodes, true);

        if (options.getOption(OptionsImpl.LABELLED_BLOCKS)) {
            Op03SimpleStatement.labelAnonymousBlocks(op03SimpleParseNodes, blockIdentifierFactory);
        }

//        if (passIdx == 0) {
//            if (Op03SimpleStatement.checkTypeClashes(op03SimpleParseNodes, bytecodeMeta)) {
//                comments.addComment(DecompilerComment.TYPE_CLASHES);
//                triggerRecovery = true;
//            }
//        }

        Op03SimpleStatement.simplifyConditionals(op03SimpleParseNodes, true);
        Op03SimpleStatement.extractExceptionMiddle(op03SimpleParseNodes);


        /*
         * At this point, if we have any remaining stack variables, then we're either looking
         * at some form of obfuscation, or non java.  Either way, replace StackValues with locals
         * (albeit locals which known that they don't have a valid lookup).
         */
        Op03SimpleStatement.replaceStackVarsWithLocals(op03SimpleParseNodes);

        if (options.getOption(OptionsImpl.SHOW_INFERRABLE, classFileVersion)) {
            Op03SimpleStatement.rewriteWith(op03SimpleParseNodes, new ExplicitTypeCallRewriter());
        }
        /*
         * It's possible to have false sharing across distinct regimes in the case of loops -
         * see LoopTest56.  We need to verify that we have not generated obviously bad code.
         * If that's the case - split the lifetime of the relevant variable more aggressively.
         */
        if (passIdx == 0 && checkLoopTypeClash) {
            if (LoopLivenessClash.detect(op03SimpleParseNodes, bytecodeMeta)) {
                comments.addComment(DecompilerComment.TYPE_CLASHES);
            }
            if (bytecodeMeta.has(BytecodeMeta.CodeInfoFlag.ITERATED_TYPE_HINTS)) {
                comments.addComment(DecompilerComment.ITERATED_TYPE_HINTS);
            }
        }

        Cleaner.reindexInPlace(op03SimpleParseNodes);
        Op04StructuredStatement block = Op03SimpleStatement.createInitialStructuredBlock(op03SimpleParseNodes);

        Op04StructuredStatement.tidyEmptyCatch(block);
        Op04StructuredStatement.tidyTryCatch(block);
        Op04StructuredStatement.convertUnstructuredIf(block);
        Op04StructuredStatement.inlinePossibles(block);
        Op04StructuredStatement.removeStructuredGotos(block);
        Op04StructuredStatement.removePointlessBlocks(block);
        Op04StructuredStatement.removePointlessReturn(block);
        Op04StructuredStatement.removePrimitiveDeconversion(options, method, block);
        if (options.getOption(OptionsImpl.LABELLED_BLOCKS)) {
            Op04StructuredStatement.insertLabelledBlocks(block);
        }
        // It seems perverse to do a second pass for removal of pointless blocks - but now everything is in place
        // the logic is much cleaner.
        Op04StructuredStatement.removeUnnecessaryLabelledBreaks(block);

        /*
         * If we can't fully structure the code, we bow out here.
         */

        if (!block.isFullyStructured()) {
            comments.addComment(DecompilerComment.UNABLE_TO_STRUCTURE);
        } else {
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
            Op04StructuredStatement.tidyVariableNames(method, block, bytecodeMeta, comments);

            Op04StructuredStatement.miscKeyholeTransforms(block);

            /*
             * Now finally run some extra checks to spot wierdness.
             */
            Op04StructuredStatement.applyChecker(new LooseCatchChecker(), block, comments);
        }

        // Only check for type clashes on first pass.
        if (passIdx == 0) {
            if (Op04StructuredStatement.checkTypeClashes(block, bytecodeMeta)) {
                comments.addComment(DecompilerComment.TYPE_CLASHES);
            }
        }

        return new AnalysisResult(comments, block, anonymousClassUsage);
    }


    public void dump(Dumper d) {
        d.newln();
        analysed.dump(d);
    }

}
