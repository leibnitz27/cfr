package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.bytestream.OffsettingByteData;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/*
 * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.4
 *
 * A stack map table contains information to help the verifier, which has a high possibility of
 * being useful for decompilation type identification.
 *
 * Note - we CANNOT blindly trust stackmaps - and DEFINITELY not in <= 50. (which is quite possibly
 * why you still see a lot of 50 classes in the wild! :) )
 *
 * http://www.oracle.com/technetwork/java/javase/compatibility-417013.html
 *
 * Area: JSR 202
 * Synopsis: Verification of Version 51.0 Class Files
 * Description: Classfiles with version number 51 are exclusively verified using the type-checking verifier,
 * and thus the methods must have StackMapTable attributes when appropriate. For classfiles with version 50,
 * the Hotspot JVM would (and continues to) failover to the type-inferencing verifier if the stackmaps in the
 * file were missing or incorrect. This failover behavior does not occur for classfiles with version 51
 * (the default version for Java SE 7).
 * Any tool that modifies bytecode in a version 51 classfile must be sure to update the stackmap information
 * to be consistent with the bytecode in order to pass verification.
 *
 * I haven't yet proved that it's possible to have a stack map table which is legal, and yet provides
 * incorrect hints.  It feels like it should be ;)
 *
 * I wonder - given that this addresses the verifier weaknesses that led to JSR being banned - why is JSR
 * banned?!
 */
public class AttributeStackMapTable extends Attribute {
    public final static String ATTRIBUTE_NAME = "StackMapTable";

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_REMAINDER = 6;
    private static final long OFFSET_OF_NUMBER_OF_ENTRIES = OFFSET_OF_REMAINDER;
    private static final long OFFSET_OF_STACK_MAP_FRAMES = 8;

    private final int length;
    private final boolean valid; // apparently, anyway!
    private final List<StackMapFrame> stackMapFrames;

    public AttributeStackMapTable(ByteData raw, ConstantPool cp) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        this.valid = false;
        this.stackMapFrames = null;
    }

    /*
     * NB : Currently unused - until I actually make use of this, doesn't seem worth consuming the
     * memory.
     */
    public AttributeStackMapTable(ByteData raw, ConstantPool cp, ClassFileVersion classFileVersion) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        int numEntries = raw.getU2At(OFFSET_OF_NUMBER_OF_ENTRIES);
        long offset = OFFSET_OF_STACK_MAP_FRAMES;
        List<StackMapFrame> frames = ListFactory.newList();
        boolean isValid = true;
        OffsettingByteData data = raw.getOffsettingOffsetData(offset);
        try {
            for (int x = 0; x < numEntries; ++x) {
                StackMapFrame frame = readStackMapFrame(data);
                frames.add(frame);
            }
        } catch (Exception e) {
            isValid = false;
        }
        this.stackMapFrames = frames;
        this.valid = isValid;
    }

    public boolean isValid() {
        return valid;
    }

    public List<StackMapFrame> getStackMapFrames() {
        return stackMapFrames;
    }

    private static StackMapFrame readStackMapFrame(OffsettingByteData raw) {
        short frameType = raw.getU1At(0);
        raw.advance(1);
        if (frameType < 64) {
            return new StackMapFrameSameFrame(frameType);
        }
        if (frameType < 127) {
            return same_locals_1_stack_item_frame(frameType, raw);
        }
        if (frameType < 247) {
            // Reserved.
            throw new IllegalStateException();
        }
        switch (frameType) {
            case 247:
                return same_locals_1_stack_item_frame_extended(raw);
            case 248:
            case 249:
            case 250:
                return chop_frame(frameType, raw);
            case 251:
                return same_frame_extended(raw);
            case 252:
            case 253:
            case 254:
                return append_frame(frameType, raw);
            case 255:
                return full_frame(raw);
            default: // can't happen.
                throw new IllegalStateException();
        }
    }

    private static StackMapFrame same_locals_1_stack_item_frame(short type, OffsettingByteData raw) {
        VerificationInfo verificationInfo = readVerificationInfo(raw);
        return new StackMapFrameSameLocals1SameItemFrame(type, verificationInfo);
    }

    private static StackMapFrame same_locals_1_stack_item_frame_extended(OffsettingByteData raw) {
        int offset_delta = raw.getU2At(0);
        raw.advance(2);
        VerificationInfo verificationInfo = readVerificationInfo(raw);
        return new StackMapFrameSameLocals1SameItemFrameExtended(offset_delta, verificationInfo);
    }

    private static StackMapFrame chop_frame(short frame_type, OffsettingByteData raw) {
        int offset_delta = raw.getU2At(0);
        raw.advance(2);
        return new StackMapFrameChopFrame(frame_type, offset_delta);
    }

    private static StackMapFrame same_frame_extended(OffsettingByteData raw) {
        int offset_delta = raw.getU2At(0);
        raw.advance(2);
        return new StackMapFrameSameFrameExtended(offset_delta);
    }

    private static StackMapFrame append_frame(short frame_type, OffsettingByteData raw) {
        int offset_delta = raw.getU2At(0);
        raw.advance(2);
        int num_ver = frame_type - 251;
        VerificationInfo[] verificationInfos = new VerificationInfo[num_ver];
        for (int x=0;x<num_ver;++x) {
            verificationInfos[x] = readVerificationInfo(raw);
        }
        return new StackMapFrameAppendFrame(frame_type, offset_delta, verificationInfos);
    }

    private static StackMapFrame full_frame(OffsettingByteData raw) {
        int offset_delta = raw.getU2At(0);
        raw.advance(2);
        int number_of_locals = raw.getU2At(0);
        raw.advance(2);
        long offset = 5;
        VerificationInfo[] verificationLocals = new VerificationInfo[number_of_locals];
        for (int x=0;x<number_of_locals;++x) {
            verificationLocals[x] = readVerificationInfo(raw);
        }
        int number_of_stack_items = raw.getU2At(0);
        raw.advance(2);
        VerificationInfo[] verificationStackItems = new VerificationInfo[number_of_stack_items];
        for (int x=0;x<number_of_stack_items;++x) {
            verificationStackItems[x] = readVerificationInfo(raw);
        }
        return new StackMapFrameFullFrame(offset_delta, verificationLocals, verificationStackItems);
    }

    private static VerificationInfo readVerificationInfo(OffsettingByteData raw) {
        short type = raw.getU1At(0);
        raw.advance(1);
        switch (type) {
            case VerificationInfoTop.TYPE:
                return VerificationInfoTop.INSTANCE;
            case VerificationInfoInteger.TYPE:
                return VerificationInfoInteger.INSTANCE;
            case VerificationInfoFloat.TYPE:
                return VerificationInfoFloat.INSTANCE;
            case VerificationInfoDouble.TYPE:
                return VerificationInfoDouble.INSTANCE;
            case VerificationInfoLong.TYPE:
                return VerificationInfoLong.INSTANCE;
            case VerificationInfoNull.TYPE:
                return VerificationInfoNull.INSTANCE;
            case VerificationInfoUninitializedThis.TYPE:
                return VerificationInfoUninitializedThis.INSTANCE;
            case VerificationInfoObject.TYPE: {
                int u2 = raw.getU2At(0);
                raw.advance(2);
                return new VerificationInfoObject(u2);
            }
            case VerificationInfoUninitialized.TYPE: {
                int u2 = raw.getU2At(0);
                raw.advance(2);
                return new VerificationInfoUninitialized(u2);
            }
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public String getRawName() {
        return ATTRIBUTE_NAME;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d;
    }

    @Override
    public long getRawByteLength() {
        return OFFSET_OF_REMAINDER + length;
    }

    private static interface StackMapFrame {
    }

    private static class StackMapFrameSameFrame implements StackMapFrame {
        private final short id;

        private StackMapFrameSameFrame(short id) {
            this.id = id;
        }

    }

    private static class StackMapFrameSameLocals1SameItemFrame implements StackMapFrame {
        private final short id;
        private final VerificationInfo verificationInfo;

        private StackMapFrameSameLocals1SameItemFrame(short id, VerificationInfo verificationInfo) {
            this.id = id;
            this.verificationInfo = verificationInfo;
        }

    }

    private static class StackMapFrameSameLocals1SameItemFrameExtended implements StackMapFrame {
        private final int offset_delta;
        private final VerificationInfo verificationInfo;

        private StackMapFrameSameLocals1SameItemFrameExtended(int offset_delta, VerificationInfo verificationInfo) {
            this.offset_delta = offset_delta;
            this.verificationInfo = verificationInfo;
        }

    }

    private static class StackMapFrameChopFrame implements StackMapFrame {
        private final short frame_type;
        private final int offset_delta;

        private StackMapFrameChopFrame(short frame_type, int offset_delta) {
            this.frame_type = frame_type;
            this.offset_delta = offset_delta;
        }

    }

    private static class StackMapFrameSameFrameExtended implements StackMapFrame {
        private final int offset_delta;

        private StackMapFrameSameFrameExtended(int offset_delta) {
            this.offset_delta = offset_delta;
        }

    }

    private static class StackMapFrameAppendFrame implements StackMapFrame {
        private final short frame_type;
        private final int offset_delta;
        private final VerificationInfo[] verificationInfos;

        private StackMapFrameAppendFrame(short frame_type, int offset_delta, VerificationInfo[] verificationInfos) {
            this.frame_type = frame_type;
            this.offset_delta = offset_delta;
            this.verificationInfos = verificationInfos;
        }

    }

    private static class StackMapFrameFullFrame implements StackMapFrame {
        private final int offset_delta;
        private final VerificationInfo[] verificationLocals;
        private final VerificationInfo[] verificationStackItems;

        private StackMapFrameFullFrame(int offset_delta, VerificationInfo[] verificationLocals, VerificationInfo[] verificationStackItems) {
            this.offset_delta = offset_delta;
            this.verificationLocals = verificationLocals;
            this.verificationStackItems = verificationStackItems;
        }

    }
    /*
     * Be nice to make this an enum, but we can't as variable info is ... variable.
     */
    private static interface VerificationInfo {
    }

    private static class AbstractVerificationInfo implements VerificationInfo {
    }

    private static class VerificationInfoTop extends AbstractVerificationInfo {
        private static final char TYPE = 0;
        private static VerificationInfo INSTANCE = new VerificationInfoTop();
    }

    private static class VerificationInfoInteger extends AbstractVerificationInfo {
        private static final char TYPE = 1;
        private static VerificationInfo INSTANCE = new VerificationInfoInteger();
    }

    private static class VerificationInfoFloat extends AbstractVerificationInfo {
        private static final char TYPE = 2;
        private static VerificationInfo INSTANCE = new VerificationInfoFloat();
    }

    private static class VerificationInfoDouble extends AbstractVerificationInfo {
        private static final char TYPE = 3;
        private static VerificationInfo INSTANCE = new VerificationInfoDouble();
    }

    private static class VerificationInfoLong extends AbstractVerificationInfo {
        private static final char TYPE = 4;
        private static VerificationInfo INSTANCE = new VerificationInfoLong();
    }

    private static class VerificationInfoNull extends AbstractVerificationInfo {
        private static final char TYPE = 5;
        private static VerificationInfo INSTANCE = new VerificationInfoNull();
    }

    private static class VerificationInfoUninitializedThis extends AbstractVerificationInfo {
        private static final char TYPE = 6;
        private static VerificationInfo INSTANCE = new VerificationInfoUninitializedThis();
    }

    private static class VerificationInfoObject implements VerificationInfo {
        private static final char TYPE = 7;
        private final int cpool_index;

        private VerificationInfoObject(int cpool_index) {
            this.cpool_index = cpool_index;
        }

    }

    private static class VerificationInfoUninitialized implements VerificationInfo {
        private static final char TYPE = 8;
        private final int offset;

        private VerificationInfoUninitialized(int offset) {
            this.offset = offset;
        }

    }


}
