package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryModuleInfo;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class AttributeModule extends Attribute {
    public static final String ATTRIBUTE_NAME = "Module";

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_MODULE_NAME = 6;
    private static final long OFFSET_OF_MODULE_FLAGS = 8;
    private static final long OFFSET_OF_MODULE_VERSION = 10;
    private static final long OFFSET_OF_DYNAMIC_INFO = 12;
    private final int nameIdx;
    private final int flags;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final int versionIdx;
    private final List<Require> requires;
    private final List<ExportOpen> exports;
    private final List<ExportOpen> opens;
    private final List<Use> uses;
    private final List<Provide> provides;

    public Set<ModuleFlags> getFlags() {
        return ModuleFlags.build(flags);
    }

    public enum ModuleFlags {
        OPEN("open"),
        SYNTHETIC("/* synthetic */"),
        MANDATED("/* mandated */");

        private final String comment;

        ModuleFlags(String comment) {
            this.comment = comment;
        }

        public static Set<ModuleFlags> build(int raw)
        {
            Set<ModuleFlags> res = new TreeSet<ModuleFlags>();
            if (0 != (raw & 0x20)) res.add(OPEN);
            if (0 != (raw & 0x1000)) res.add(SYNTHETIC);
            if (0 != (raw & 0x8000)) res.add(MANDATED);
            return res;
        }

        @Override
        public String toString() {
            return comment;
        }
    }

    public enum ModuleContentFlags {
        TRANSITIVE("/* transitive */"),
        STATIC_PHASE("/* static phase */"),
        SYNTHETIC("/* synthetic */"),
        MANDATED("/* mandated */");

        private final String comment;

        ModuleContentFlags(String comment) {
            this.comment = comment;
        }

        public static Set<ModuleContentFlags> build(int raw)
        {
            Set<ModuleContentFlags> res = new TreeSet<ModuleContentFlags>();
            if (0 != (raw & 0x20)) res.add(TRANSITIVE);
            if (0 != (raw & 0x40)) res.add(STATIC_PHASE);
            if (0 != (raw & 0x1000)) res.add(SYNTHETIC);
            if (0 != (raw & 0x8000)) res.add(MANDATED);
            return res;
        }

        @Override
        public String toString() {
            return comment;
        }
    }

    public static class Require {
        private final int index;
        private final int flags;
        @SuppressWarnings("unused")
        private final int version_index;

        public int getIndex() {
            return index;
        }

        public Set<ModuleContentFlags> getFlags() {
            return ModuleContentFlags.build(flags);
        }

        private Require(int index, int flags, int version_index) {
            this.index = index;
            this.flags = flags;
            this.version_index = version_index;
        }

        private static long read(ByteData raw, long offset, List<Require> tgt) {
            int num = raw.getU2At(offset);
            offset += 2;
            for (int x=0;x<num;++x) {
                tgt.add(new Require(raw.getU2At(offset), raw.getU2At(offset+2), raw.getU2At(offset+4)));
                offset += 6;
            }
            return offset;
        }
    }

    public static class ExportOpen {
        private final int index;
        private final int flags;
        private final int[] to_index;

        private ExportOpen(int index, int flags, int[] to_index) {
            this.index = index;
            this.flags = flags;
            this.to_index = to_index;
        }

        public Set<ModuleContentFlags> getFlags() {
            return ModuleContentFlags.build(flags);
        }

        public int getIndex() {
            return index;
        }

        public int[] getToIndex() {
            return to_index;
        }

        private static long read(ByteData raw, long offset, List<ExportOpen> tgt) {
            int num = raw.getU2At(offset);
            offset += 2;
            for (int x=0;x<num;++x) {
                int index = raw.getU2At(offset);
                int flags = raw.getU2At(offset+2);
                int count = raw.getU2At(offset+4);
                offset += 6;
                int[] indices = new int[count];
                for (int y=0;y<count;++y) {
                    indices[y] = raw.getU2At(offset);
                    offset += 2;
                }
                tgt.add(new ExportOpen(index, flags, indices));
            }
            return offset;
        }

    }

    public static class Use {
        int index;

        private Use(int index) {
            this.index = index;
        }

        private static long read(ByteData raw, long offset, List<Use> tgt) {
            int num = raw.getU2At(offset);
            offset += 2;
            for (int x=0;x<num;++x) {
                int index = raw.getU2At(offset);
                tgt.add(new Use(index));
                offset += 2;
            }
            return offset;
        }

    }

    public static class Provide {
        private final int index;
        private final int[] with_index;

        private Provide(int index, int[] with_index) {
            this.index = index;
            this.with_index = with_index;
        }

        public int getIndex() {
            return index;
        }

        public int[] getWithIndex() {
            return with_index;
        }

        private static void read(ByteData raw, long offset, List<Provide> tgt) {
            int num = raw.getU2At(offset);
            offset += 2;
            for (int x=0;x<num;++x) {
                int index = raw.getU2At(offset);
                int count = raw.getU2At(offset+2);
                offset += 4;
                int[] indices = new int[count];
                for (int y=0;y<count;++y) {
                    indices[y] = raw.getU2At(offset);
                    offset += 2;
                }
                tgt.add(new Provide(index, indices));
            }
        }

    }

    // requires, exports, opens, uses and provides are dynamically sized.

    private final int length;
    private ConstantPool cp;

    public AttributeModule(ByteData raw, ConstantPool cp) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        this.cp = cp;
        this.nameIdx = raw.getU2At(OFFSET_OF_MODULE_NAME);
        this.flags = raw.getU2At(OFFSET_OF_MODULE_FLAGS);
        this.versionIdx = raw.getU2At(OFFSET_OF_MODULE_VERSION);
        long offset = OFFSET_OF_DYNAMIC_INFO;
        this.requires = ListFactory.newList();
        this.exports = ListFactory.newList();
        this.opens = ListFactory.newList();
        this.uses = ListFactory.newList();
        this.provides = ListFactory.newList();
        offset = Require.read(raw, offset, this.requires);
        offset = ExportOpen.read(raw, offset, this.exports);
        offset = ExportOpen.read(raw, offset, this.opens);
        offset = Use.read(raw, offset, this.uses);
        Provide.read(raw, offset, this.provides);
    }

    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print(ATTRIBUTE_NAME);
    }

    @Override
    public long getRawByteLength() {
        return OFFSET_OF_MODULE_FLAGS + length;
    }

    @Override
    public String toString() {
        return ATTRIBUTE_NAME;
    }

    public List<Require> getRequires() {
        return requires;
    }

    public List<ExportOpen> getExports() {
        return exports;
    }

    public List<ExportOpen> getOpens() {
        return opens;
    }

    public List<Use> getUses() {
        return uses;
    }

    public List<Provide> getProvides() {
        return provides;
    }

    public ConstantPool getCp() {
        return cp;
    }

    public String getModuleName() {
        return ((ConstantPoolEntryModuleInfo)cp.getEntry(nameIdx)).getName().getValue();
    }


}
