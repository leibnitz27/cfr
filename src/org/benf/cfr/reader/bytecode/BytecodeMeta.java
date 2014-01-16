package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.entities.attributes.AttributeCode;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.EnumSet;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 16/01/2014
 * Time: 06:40
 */
public class BytecodeMeta {
    public enum CodeInfoFlag {
        USES_MONITORS,
        USES_EXCEPTIONS
    }

    private final EnumSet<CodeInfoFlag> flags = EnumSet.noneOf(CodeInfoFlag.class);

    /*
     * We could generate op1s from code - but we already had them......
     */
    public BytecodeMeta(List<Op01WithProcessedDataAndByteJumps> op1s, AttributeCode code) {
        int flagCount = CodeInfoFlag.values().length;
        if (!code.getExceptionTableEntries().isEmpty()) flags.add(CodeInfoFlag.USES_EXCEPTIONS);
        for (Op01WithProcessedDataAndByteJumps op : op1s) {
            switch (op.getJVMInstr()) {
                case MONITOREXIT:
                case MONITORENTER:
                    flags.add(CodeInfoFlag.USES_MONITORS);
                    break;
            }
            // Don't bother processing any longer if we've found all the flags!
            if (flags.size() == flagCount) return;
        }
    }

    public boolean has(CodeInfoFlag flag) {
        return flags.contains(flag);
    }

    private static class FlagTest implements UnaryFunction<BytecodeMeta, Boolean> {
        private final CodeInfoFlag flag;

        private FlagTest(CodeInfoFlag flag) {
            this.flag = flag;
        }

        @Override
        public Boolean invoke(BytecodeMeta arg) {
            return arg.has(flag);
        }
    }

    public static UnaryFunction<BytecodeMeta, Boolean> testFlag(CodeInfoFlag flag) {
        return new FlagTest(flag);
    }

}
