package org.benf.cfr.reader.bytecode;

import com.sun.tools.javac.comp.Infer;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op01WithProcessedDataAndByteJumps;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.attributes.AttributeCode;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BytecodeMeta {
    public enum CodeInfoFlag {
        USES_MONITORS,
        USES_EXCEPTIONS,
        USES_INVOKEDYNAMIC,
        LIVENESS_CLASH,
        ITERATED_TYPE_HINTS
    }

    private final EnumSet<CodeInfoFlag> flags = EnumSet.noneOf(CodeInfoFlag.class);

    private final Set<Integer> livenessClashes = SetFactory.newSet();
    private final Map<Integer, JavaTypeInstance> iteratedTypeHints = MapFactory.newMap();

    public BytecodeMeta(List<Op01WithProcessedDataAndByteJumps> op1s, AttributeCode code) {
        int flagCount = CodeInfoFlag.values().length;
        if (!code.getExceptionTableEntries().isEmpty()) flags.add(CodeInfoFlag.USES_EXCEPTIONS);
        for (Op01WithProcessedDataAndByteJumps op : op1s) {
            switch (op.getJVMInstr()) {
                case MONITOREXIT:
                case MONITORENTER:
                    flags.add(CodeInfoFlag.USES_MONITORS);
                    break;
                case INVOKEDYNAMIC:
                    flags.add(CodeInfoFlag.USES_INVOKEDYNAMIC);
                    break;
            }
            // Don't bother processing any longer if we've found all the flags!
            if (flags.size() == flagCount) return;
        }
    }

    public boolean has(CodeInfoFlag flag) {
        return flags.contains(flag);
    }

    public void informLivenessClashes(Set<Integer> slots) {
        flags.add(CodeInfoFlag.LIVENESS_CLASH);
        livenessClashes.addAll(slots);
    }

    public void takeIteratedTypeHint(InferredJavaType inferredJavaType, JavaTypeInstance itertype) {
        int bytecodeIdx = inferredJavaType.getTaggedBytecodeLocation();
        if (bytecodeIdx < 0) return;
        Integer key = bytecodeIdx;
        if (iteratedTypeHints.containsKey(key)) {
            JavaTypeInstance already = iteratedTypeHints.get(key);
            if (already == null) return;
            if (!itertype.equals(already)) {
                iteratedTypeHints.put(key, null);
            }
        } else {
            flags.add(CodeInfoFlag.ITERATED_TYPE_HINTS);
            iteratedTypeHints.put(key, itertype);
        }
    }

    public Map<Integer, JavaTypeInstance> getIteratedTypeHints() {
           return iteratedTypeHints;
    }

    public Set<Integer> getLivenessClashes() {
        return livenessClashes;
    }

    private static class FlagTest implements UnaryFunction<BytecodeMeta, Boolean> {
        private final CodeInfoFlag[] flags;

        private FlagTest(CodeInfoFlag[] flags) {
            this.flags = flags;
        }

        @Override
        public Boolean invoke(BytecodeMeta arg) {
            for (CodeInfoFlag flag : flags) {
                if (arg.has(flag)) return true;
            }
            return false;
        }
    }

    public static UnaryFunction<BytecodeMeta, Boolean> hasAnyFlag(CodeInfoFlag... flag) {
        return new FlagTest(flag);
    }

}
