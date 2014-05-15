package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;

public class DeadMethodRemover {

    public static void removeDeadMethod(ClassFile classFile, Method method) {
        Op04StructuredStatement code = method.getAnalysis();
        StructuredStatement statement = code.getStatement();
        if (!(statement instanceof Block)) return;

        Block block = (Block) statement;
        for (Op04StructuredStatement inner : block.getBlockStatements()) {
            StructuredStatement innerStatement = inner.getStatement();
            if (!(innerStatement instanceof StructuredComment)) {
                return;
            }
        }

        classFile.removePointlessMethod(method);
    }

}
