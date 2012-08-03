package org.benf.cfr.reader.bytecode.analysis.structured.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created:
 * User: lee
 * Date: 15/05/2012
 */
public class StructuredIter extends AbstractStructuredStatement {
    private final BlockIdentifier block;
    private final LValue iterator;
    private final Expression list;
    private final Op04StructuredStatement body;
    private final JavaTypeInstance itertype;

    public StructuredIter(BlockIdentifier block, LValue iterator, Expression list, Op04StructuredStatement body) {
        this.block = block;
        this.iterator = iterator;
        this.list = list;
        this.body = body;
        /*
         * We need to be able to type the iterator.
         */
        this.itertype = iterator.getInferredJavaType().getJavaTypeInstance();
        if (!itertype.isUsableType()) {
            //      throw new ConfusedCFRException("Not a usable type for an iter");
        }
    }

    @Override
    public void dump(Dumper dumper) {
        if (block.hasForeignReferences()) dumper.print(block.getName() + " : ");
        dumper.print("for (" + itertype + " " + iterator + " : " + list + ") ");
        body.dump(dumper);
    }


}
