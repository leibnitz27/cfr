package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.GraphConversionHelper;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamerFactory;
import org.benf.cfr.reader.bytecode.analysis.stack.StackSim;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.attributes.AttributeCode;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.exceptions.ExceptionAggregator;
import org.benf.cfr.reader.entities.exceptions.ExceptionBookmark;
import org.benf.cfr.reader.entities.exceptions.ExceptionTableEntry;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.bytestream.OffsettingByteData;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 28/04/2011
 * Time: 07:20
 * To change this template use File | Settings | File Templates.
 */
public class CodeAnalyser {

    private final AttributeCode originalCodeAttribute;
    private final ConstantPool cp;
    private Dumpable start;

    public CodeAnalyser(AttributeCode attributeCode) {
        this.originalCodeAttribute = attributeCode;
        this.cp = attributeCode.getConstantPool();
    }

    public void analyse() {

        ByteData rawCode = originalCodeAttribute.getRawData();
        long codeLength = originalCodeAttribute.getCodeLength();
        ArrayList<Op01WithProcessedDataAndByteJumps> instrs = new ArrayList<Op01WithProcessedDataAndByteJumps>();
        Map<Integer, Integer> lutByOffset = new HashMap<Integer, Integer>();
        Map<Integer, Integer> lutByIdx = new HashMap<Integer, Integer>();
        OffsettingByteData bdCode = rawCode.getOffsettingOffsetData(0);
        int idx = 0;
        int offset = 0;

        do {
            JVMInstr instr = JVMInstr.find(bdCode.getS1At(0));
            Op01WithProcessedDataAndByteJumps oc = instr.createOperation(bdCode, cp, offset);
            System.out.println(oc);
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

        /* Exceptions block */
        if (true) {
            // Add entries from the exception table.  Since these are stored in terms of offsets, they're
            // only usable here until we mess around with the instruction structure, so do it early!
            ExceptionAggregator exceptions = new ExceptionAggregator(originalCodeAttribute.getExceptionTableEntries());
            List<Short> exceptionStarts = exceptions.getExceptionHandlerStarts();
            for (Short exception_from : exceptionStarts) {
                List<ExceptionTableEntry> rawes = exceptions.getExceptionsFromSource(exception_from);
                int originalIndex = lutByOffset.get((int) exception_from);
                Op02WithProcessedDataAndRefs startInstruction = op2list.get(originalIndex);

                List<Op02WithProcessedDataAndRefs> handlerTargets = ListFactory.newList();
                for (ExceptionTableEntry exceptionTableEntry : rawes) {
                    short handler = exceptionTableEntry.getBytecode_index_handler();
                    int handlerIndex = lutByOffset.get((int) handler);
                    Op02WithProcessedDataAndRefs handerTarget = op2list.get(handlerIndex);
                    handlerTargets.add(handerTarget);
                }

                // Unlink startInstruction from its source, add a new instruction in there, which has a
                // default target of startInstruction, but additional targets of handlerTargets.
                ExceptionBookmark exceptionBookmark = new ExceptionBookmark(rawes);
                Op02WithProcessedDataAndRefs tryOp =
                        new Op02WithProcessedDataAndRefs(JVMInstr.FAKE_TRY, null, startInstruction.getIndex(), -1, cp, null, -1, exceptionBookmark);

                if (startInstruction.getSources().isEmpty())
                    throw new ConfusedCFRException("Can't install exception handler infront of nothing");
                for (Op02WithProcessedDataAndRefs source : startInstruction.getSources()) {
                    source.replaceTarget(startInstruction, tryOp);
                }
                tryOp.addTarget(startInstruction);
                for (Op02WithProcessedDataAndRefs tryTarget : handlerTargets) {
                    Op02WithProcessedDataAndRefs preCatchOp =
                            new Op02WithProcessedDataAndRefs(JVMInstr.FAKE_CATCH, null, tryTarget.getIndex(), -1, cp, null, -1, null);

                    op2list.add(preCatchOp);

                    tryOp.addTarget(preCatchOp);
                    preCatchOp.addSource(tryOp);
                    preCatchOp.addTarget(tryTarget);
                    tryTarget.addSource(preCatchOp);
                }
                startInstruction.clearSources();
                startInstruction.addSource(tryOp);
                op2list.add(tryOp);
            }
        }


        // This dump block only exists because we're debugging bad stack size calcuations.
        Op02WithProcessedDataAndRefs o2start = op2list.get(0);
        try {
            o2start.populateStackInfo(new StackSim());
        } catch (ConfusedCFRException e) {
            Dumper dmp = new Dumper();
            dmp.print("----[known stack info]------------\n\n");
            for (Op02WithProcessedDataAndRefs op : op2list) {
                op.dump(dmp);
            }
            throw e;
        }


        this.start = o2start;


        final VariableNamer variableNamer = VariableNamerFactory.getNamer(originalCodeAttribute.getLocalVariableTable(), cp);


        final List<Op03SimpleStatement> op03SimpleParseNodes = ListFactory.newList();
        // Convert the op2s into a simple set of statements.
        // Do these need to be processed in a sensible order?  Could just iterate?
        final GraphConversionHelper conversionHelper = new GraphConversionHelper();
        // By only processing reachable bytecode, we ignore deliberate corruption.   However, we could
        // Nop out unreachable code, so as to not have this ugliness.
        GraphVisitor o2Converter = new GraphVisitorDFS(o2start,
                new BinaryProcedure<Op02WithProcessedDataAndRefs, GraphVisitor<Op02WithProcessedDataAndRefs>>() {
                    @Override
                    public void call(Op02WithProcessedDataAndRefs arg1, GraphVisitor<Op02WithProcessedDataAndRefs> arg2) {
                        Op03SimpleStatement res = new Op03SimpleStatement(arg1, arg1.createStatement(variableNamer));
                        conversionHelper.registerOriginalAndNew(arg1, res);
                        op03SimpleParseNodes.add(res);
                        for (Op02WithProcessedDataAndRefs target : arg1.getTargets()) {
                            arg2.enqueue(target);
                        }
                    }
                }
        );
        o2Converter.process();
        conversionHelper.patchUpRelations();
        List<Op03SimpleStatement> op03SimpleParseNodes2 = op03SimpleParseNodes;
        // Expand any 'multiple' statements (eg from dups)

        Op03SimpleStatement.flattenCompoundStatements(op03SimpleParseNodes2);
        op03SimpleParseNodes2 = Op03SimpleStatement.renumber(op03SimpleParseNodes2);

        Op03SimpleStatement.assignSSAIdentifiers(op03SimpleParseNodes2);

        // Condense pointless assignments
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes2);
        op03SimpleParseNodes2 = Op03SimpleStatement.renumber(op03SimpleParseNodes2);
        // Rewrite new / constructor pairs.
        Op03SimpleStatement.condenseConstruction(op03SimpleParseNodes2);
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes2);
        op03SimpleParseNodes2 = Op03SimpleStatement.renumber(op03SimpleParseNodes2);
        // Condense again!
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes2);
        op03SimpleParseNodes2 = Op03SimpleStatement.renumber(op03SimpleParseNodes2);
        // Collapse conditionals into || / &&
        Op03SimpleStatement.condenseConditionals(op03SimpleParseNodes2);
        op03SimpleParseNodes2 = Op03SimpleStatement.renumber(op03SimpleParseNodes2);

        Op03SimpleStatement o3start = op03SimpleParseNodes2.get(0);
        this.start = o3start;
    }


    public void dump(Dumper d) {
        d.newln();
        start.dump(d);
    }

}
