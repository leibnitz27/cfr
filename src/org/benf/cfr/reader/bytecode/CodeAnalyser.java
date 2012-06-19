package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.VariableNamerFactory;
import org.benf.cfr.reader.bytecode.opcode.JVMInstr;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.attributes.AttributeCode;
import org.benf.cfr.reader.entities.exceptions.ExceptionAggregator;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.bytestream.OffsettingByteData;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        int idx = 1;
        int offset = 0;

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

        BlockIdentifierFactory blockIdentifierFactory = new BlockIdentifierFactory();
        ExceptionAggregator exceptions = new ExceptionAggregator(originalCodeAttribute.getExceptionTableEntries(), blockIdentifierFactory, cp);
        //
        // We know the ranges covered by each exception handler - insert try / catch statements around
        // these ranges.
        //
        op2list = Op02WithProcessedDataAndRefs.insertExceptionBlocks(op2list, exceptions, lutByOffset, cp);
        lutByOffset = null; // No longer valid.


        // Populate stack info (each instruction gets references to stack objects
        // consumed / produced.
        Op02WithProcessedDataAndRefs.populateStackInfo(op2list);

        // Variable namer - if we've got variable names provided as an attribute in the class file, we'll
        // use that.
        final VariableNamer variableNamer = VariableNamerFactory.getNamer(originalCodeAttribute.getLocalVariableTable(), cp);

        Dumper dumper = new Dumper();

        dumper.dump(op2list);

        // Create a non final version...
        List<Op03SimpleStatement> op03SimpleParseNodes = Op02WithProcessedDataAndRefs.convertToOp03List(op2list, variableNamer);

//        dumper.print("Raw Op3 statements:\n");
//        op03SimpleParseNodes.get(0).dump(dumper);


        // Expand any 'multiple' statements (eg from dups)
        Op03SimpleStatement.flattenCompoundStatements(op03SimpleParseNodes);
        // Remove 2nd (+) jumps in pointless jump chains.
        Op03SimpleStatement.removePointlessJumps(op03SimpleParseNodes);

        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        Op03SimpleStatement.assignSSAIdentifiers(op03SimpleParseNodes);

        // Condense pointless assignments
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);

        // Rewrite new / constructor pairs.
        Op03SimpleStatement.condenseConstruction(op03SimpleParseNodes);
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);
        // Condense again, now we've simplified constructors.
        Op03SimpleStatement.condenseLValues(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);
        // Collapse conditionals into || / &&
        Op03SimpleStatement.condenseConditionals(op03SimpleParseNodes);
        Op03SimpleStatement.simplifyConditionals(op03SimpleParseNodes);
        op03SimpleParseNodes = Op03SimpleStatement.renumber(op03SimpleParseNodes);
//
//        dumper.print("Raw Op3 statements:\n");
//        op03SimpleParseNodes.get(0).dump(dumper);
//

        // Rewrite conditionals which jump into an immediate jump (see specifics)
        Op03SimpleStatement.rewriteNegativeJumps(op03SimpleParseNodes);

        // Identify simple while loops.
        Op03SimpleStatement.identifyLoops1(op03SimpleParseNodes, blockIdentifierFactory);

        // Perform this before simple forward if detection, as it allows us to not have to consider
        // gotos which have been relabelled as continue/break.
        Op03SimpleStatement.rewriteBreakStatements(op03SimpleParseNodes);
        Op03SimpleStatement.rewriteWhilesAsFors(op03SimpleParseNodes);

        // identify conditionals which are of the form if (a) { xx } [ else { yy } ]
        // where xx and yy have no GOTOs in them.
        Op03SimpleStatement.identifyNonjumpingConditionals(op03SimpleParseNodes, blockIdentifierFactory);


        /*
         * Convert the Simple Statements into one structured Statement.
         */
        dumper.print("FINAL Op03SimpleStatement NODES:\n\n************\n");
        op03SimpleParseNodes.get(0).dump(dumper);

        Op04StructuredStatement block = Op03SimpleStatement.createInitialStructuredBlock(op03SimpleParseNodes);

        block.dump(dumper);
        this.start = block;
    }


    public void dump(Dumper d) {
        d.newln();
        start.dump(d);
    }

}
