package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/05/2013
 * Time: 17:27
 */
public class MiscStatementTools {
    public static List<Op04StructuredStatement> getBlockStatements(Op04StructuredStatement code) {
        StructuredStatement topCode = code.getStatement();
        if (!(topCode instanceof Block)) return null;

        Block block = (Block) topCode;
        List<Op04StructuredStatement> statements = block.getBlockStatements();
        return statements;
    }
}
