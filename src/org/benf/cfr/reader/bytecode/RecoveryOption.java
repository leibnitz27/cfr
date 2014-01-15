package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.getopt.MutableOptions;
import org.benf.cfr.reader.util.getopt.PermittedOptionProvider;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/01/2014
 * Time: 16:48
 */
public abstract class RecoveryOption<T> {

    protected final PermittedOptionProvider.Argument<T> arg;
    protected final T value;
    protected final DecompilerComment decompilerComment;

    public RecoveryOption(PermittedOptionProvider.Argument<T> arg, T value) {
        this.arg = arg;
        this.value = value;
        this.decompilerComment = null;
    }

    public RecoveryOption(PermittedOptionProvider.Argument<T> arg, T value, DecompilerComment comment) {
        this.arg = arg;
        this.value = value;
        this.decompilerComment = comment;
    }

    protected boolean applyComment(boolean applied, List<DecompilerComment> commentList) {
        if (!applied) return false;
        if (decompilerComment == null) return true;
        commentList.add(decompilerComment);
        return true;
    }

    public abstract boolean apply(MutableOptions mutableOptions, List<DecompilerComment> commentList);

    public static class TrooleanRO extends RecoveryOption<Troolean> {
        public TrooleanRO(PermittedOptionProvider.Argument<Troolean> arg, Troolean value) {
            super(arg, value);
        }

        public TrooleanRO(PermittedOptionProvider.Argument<Troolean> arg, Troolean value, DecompilerComment comment) {
            super(arg, value, comment);
        }

        public boolean apply(MutableOptions mutableOptions, List<DecompilerComment> commentList) {
            return applyComment(mutableOptions.override(arg, value), commentList);
        }
    }

    public static class BooleanRO extends RecoveryOption<Boolean> {
        public BooleanRO(PermittedOptionProvider.Argument<Boolean> arg, boolean value) {
            super(arg, value);
        }

        public BooleanRO(PermittedOptionProvider.Argument<Boolean> arg, boolean value, DecompilerComment comment) {
            super(arg, value, comment);
        }

        public boolean apply(MutableOptions mutableOptions, List<DecompilerComment> commentList) {
            return applyComment(mutableOptions.override(arg, value), commentList);
        }
    }
}
