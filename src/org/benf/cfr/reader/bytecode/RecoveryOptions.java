package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.getopt.MutableOptions;
import org.benf.cfr.reader.util.getopt.Options;

import java.util.List;

public class RecoveryOptions {
    private final List<RecoveryOption<?>> recoveryOptions;

    public RecoveryOptions(RecoveryOption<?>... recoveryOptions) {
        this.recoveryOptions = ListFactory.newImmutableList(recoveryOptions);
    }

    public RecoveryOptions(RecoveryOptions prev, RecoveryOption<?>... recoveryOptions) {
        List<RecoveryOption<?>> recoveryOptionList = ListFactory.newImmutableList(recoveryOptions);
        this.recoveryOptions = ListFactory.newList();
        this.recoveryOptions.addAll(prev.recoveryOptions);
        this.recoveryOptions.addAll(recoveryOptionList);
    }

    public static class Applied {
        public Options options;
        public List<DecompilerComment> comments;
        public boolean valid;

        public Applied(Options options, List<DecompilerComment> comments, boolean valid) {
            this.options = options;
            this.comments = comments;
            this.valid = valid;
        }
    }

    public Applied apply(DCCommonState commonState, Options originalOptions, BytecodeMeta bytecodeMeta) {
        MutableOptions mutableOptions = new MutableOptions(originalOptions);
        List<DecompilerComment> appliedComments = ListFactory.newList();
        boolean hadEffect = false;
        for (RecoveryOption<?> option : recoveryOptions) {
            if (option.apply(mutableOptions, appliedComments, bytecodeMeta)) hadEffect = true;
        }
        return new Applied(mutableOptions, appliedComments, hadEffect);
    }
}
