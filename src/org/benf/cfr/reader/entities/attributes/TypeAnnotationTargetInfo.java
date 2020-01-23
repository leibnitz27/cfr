package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;

import java.util.List;

public interface TypeAnnotationTargetInfo {

    class TypeAnnotationParameterTarget implements TypeAnnotationTargetInfo {
        private final short type_parameter_index;

        TypeAnnotationParameterTarget(short type_parameter_index) {
            this.type_parameter_index = type_parameter_index;
        }

        static Pair<Long, TypeAnnotationTargetInfo> Read(ByteData raw, long offset) {
            short type_parameter_index = raw.getU1At(offset++);
            return Pair.<Long, TypeAnnotationTargetInfo>make(offset, new TypeAnnotationParameterTarget(type_parameter_index));
        }

        public short getIndex() {
            return type_parameter_index;
        }
    }

    class TypeAnnotationSupertypeTarget implements TypeAnnotationTargetInfo {
        private final int supertype_index;

        private TypeAnnotationSupertypeTarget(int supertype_index) {
            this.supertype_index = supertype_index;
        }

        static Pair<Long, TypeAnnotationTargetInfo> Read(ByteData raw, long offset) {
            int supertype_index = raw.getU2At(offset);
            offset += 2;
            return Pair.<Long, TypeAnnotationTargetInfo>make(offset, new TypeAnnotationSupertypeTarget(supertype_index));
        }

    }

    class TypeAnnotationParameterBoundTarget implements TypeAnnotationTargetInfo {
        private final short type_parameter_index;
        private final short bound_index;

        private TypeAnnotationParameterBoundTarget(short type_parameter_index, short bound_index) {
            this.type_parameter_index = type_parameter_index;
            this.bound_index = bound_index;
        }

        static Pair<Long, TypeAnnotationTargetInfo> Read(ByteData raw, long offset) {
            short type_parameter_index = raw.getU1At(offset++);
            short bound_index = raw.getU1At(offset++);
            return Pair.<Long, TypeAnnotationTargetInfo>make(offset, new TypeAnnotationParameterBoundTarget(type_parameter_index, bound_index));
        }

        public short getIndex() {
            return type_parameter_index;
        }
    }

    class TypeAnnotationEmptyTarget implements TypeAnnotationTargetInfo {
        private static TypeAnnotationTargetInfo INSTANCE = new TypeAnnotationEmptyTarget();
        static Pair<Long, TypeAnnotationTargetInfo> Read(ByteData raw, long offset) {
            return Pair.<Long, TypeAnnotationTargetInfo>make(offset, INSTANCE);
        }

        public static TypeAnnotationTargetInfo getInstance() {
            return INSTANCE;
        }
    }

    class TypeAnnotationFormalParameterTarget implements TypeAnnotationTargetInfo {
        private final short formal_parameter_index;

        private TypeAnnotationFormalParameterTarget(short formal_parameter_index) {
            this.formal_parameter_index = formal_parameter_index;
        }

        static Pair<Long, TypeAnnotationTargetInfo> Read(ByteData raw, long offset) {
            short formal_parameter_index = raw.getU1At(offset++);
            return Pair.<Long, TypeAnnotationTargetInfo>make(offset, new TypeAnnotationFormalParameterTarget(formal_parameter_index));
        }

        public int getIndex() {
            return formal_parameter_index;
        }
    }

    class TypeAnnotationThrowsTarget implements TypeAnnotationTargetInfo {
        private final int throws_type_index;

        private TypeAnnotationThrowsTarget(int throws_type_index) {
            this.throws_type_index = throws_type_index;
        }

        static Pair<Long, TypeAnnotationTargetInfo> Read(ByteData raw, long offset) {
            int throws_type_index = raw.getU2At(offset);
            offset += 2;
            return Pair.<Long, TypeAnnotationTargetInfo>make(offset, new TypeAnnotationThrowsTarget(throws_type_index));
        }

        public int getIndex() {
            return throws_type_index;
        }
    }

    class LocalVarTarget {
        private final int start;
        private final int length;
        private final int index;

        LocalVarTarget(int start, int length, int index) {
            this.start = start;
            this.length = length;
            this.index = index;
        }

        public boolean matches(int offset, int slot, int tolerance) {
            return offset >= start-tolerance && offset < start +length && slot == index;
        }
    }

    class TypeAnnotationLocalVarTarget implements TypeAnnotationTargetInfo {

        private final List<LocalVarTarget> targets;

        TypeAnnotationLocalVarTarget(List<LocalVarTarget> targets) {
            this.targets = targets;
        }

        public boolean matches(int offset, int slot, int tolerance) {
            for (LocalVarTarget tgt : targets) {
                if (tgt.matches(offset, slot, tolerance)) return true;
            }
            return false;
        }

        static Pair<Long, TypeAnnotationTargetInfo> Read(ByteData raw, long offset) {
            int count = raw.getU2At(offset);
            offset += 2;
            List<LocalVarTarget> targetList = ListFactory.newList();
            for (int x=0;x<count;++x) {
                int start = raw.getU2At(offset);
                offset += 2;
                int length = raw.getU2At(offset);
                offset += 2;
                int index = raw.getU2At(offset);
                offset += 2;
                targetList.add(new LocalVarTarget(start, length, index));
            }
            return Pair.<Long, TypeAnnotationTargetInfo>make(offset, new TypeAnnotationLocalVarTarget(targetList));
        }
    }

    class TypeAnnotationCatchTarget implements TypeAnnotationTargetInfo {
        private final int exception_table_index;

        private TypeAnnotationCatchTarget(int exception_table_index) {
            this.exception_table_index = exception_table_index;
        }

        static Pair<Long, TypeAnnotationTargetInfo> Read(ByteData raw, long offset) {
            int exception_table_index = raw.getU2At(offset);
            offset += 2;
            return Pair.<Long, TypeAnnotationTargetInfo>make(offset, new TypeAnnotationCatchTarget(exception_table_index));
        }

    }

    class TypeAnnotationOffsetTarget implements TypeAnnotationTargetInfo {
        private final int offset;

        private TypeAnnotationOffsetTarget(int offset) {
            this.offset = offset;
        }

        static Pair<Long, TypeAnnotationTargetInfo> Read(ByteData raw, long offset) {
            int offset_val = raw.getU2At(offset);
            offset += 2;
            return Pair.<Long, TypeAnnotationTargetInfo>make(offset, new TypeAnnotationOffsetTarget(offset_val));
        }
    }

    class TypeAnnotationTypeArgumentTarget implements TypeAnnotationTargetInfo {
        private final int offset;
        private final short type_argument_index;

        private TypeAnnotationTypeArgumentTarget(int offset, short type_argument_index) {
            this.offset = offset;
            this.type_argument_index = type_argument_index;
        }

        static Pair<Long, TypeAnnotationTargetInfo> Read(ByteData raw, long offset) {
            int offset_val = raw.getU2At(offset);
            offset += 2;
            short type_argument_index = raw.getU1At(offset++);
            return Pair.<Long, TypeAnnotationTargetInfo>make(offset, new TypeAnnotationTypeArgumentTarget(offset_val, type_argument_index));
        }
    }


}
