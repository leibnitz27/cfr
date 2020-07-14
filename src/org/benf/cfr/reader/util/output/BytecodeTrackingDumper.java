package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.loc.HasByteCodeLoc;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.Collection;
import java.util.Map;

class BytecodeTrackingDumper extends DelegatingDumper {

    private Map<Method, MethodBytecode> perMethod = MapFactory.newLazyMap(MapFactory.<Method, MethodBytecode>newIdentityMap(), new UnaryFunction<Method, MethodBytecode>() {
        @Override
        public MethodBytecode invoke(Method arg) {
            return new MethodBytecode();
        }
    });

    BytecodeTrackingDumper(Dumper dumper) {
        super(dumper);
    }

    @Override
    public void informBytecodeLoc(HasByteCodeLoc loc) {
        BytecodeLoc locInfo = loc.getCombinedLoc();
        int currentDepth = delegate.getIndentLevel();
        int currentLine = delegate.getCurrentLine();

        for (Method method : locInfo.getMethods()) {
            perMethod.get(method).add(locInfo.getOffsetsForMethod(method), currentDepth, currentLine);
        }
    }

    class LocAtLine {
        int depth;
        int currentLine;

        LocAtLine(int depth, int currentLine) {
            this.depth = depth;
            this.currentLine = currentLine;
        }

        public int getDepth() {
            return depth;
        }

        public int getCurrentLine() {
            return currentLine;
        }

        public void improve(int currentDepth, int currentLine) {
            if (currentDepth > depth) {
                depth = currentDepth;
                this.currentLine = currentLine;
            }
        }
    }

    class MethodBytecode {
        Map<Integer, LocAtLine> locAtLineMap = MapFactory.newTreeMap();
        public void add(Collection<Integer> offsetsForMethod, int currentDepth, int currentLine) {
            for (Integer offset : offsetsForMethod) {
                LocAtLine known = locAtLineMap.get(offset);
                if (known == null) {
                    locAtLineMap.put(offset, new LocAtLine(currentDepth, currentLine));
                } else {
                    known.improve(currentDepth, currentLine);
                }
            }
        }
    }
}
