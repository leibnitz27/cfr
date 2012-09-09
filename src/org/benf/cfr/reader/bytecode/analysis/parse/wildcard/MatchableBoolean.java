package org.benf.cfr.reader.bytecode.analysis.parse.wildcard;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/09/2012
 * <p/>
 * Need a matchable boolean as otherwise we can't wildcard boolean members without
 * specific support in every class which uses them.
 */
public abstract class MatchableBoolean {
    public static final MatchableBoolean TRUE = new MatchableBooleanImpl(true);
    public static final MatchableBoolean FALSE = new MatchableBooleanImpl(false);

    public abstract boolean getValue();

    public static MatchableBoolean get(boolean value) {
        return value ? TRUE : FALSE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MatchableBoolean)) return false;
        return this.getValue() == ((MatchableBoolean) o).getValue();
    }

    private static final class MatchableBooleanImpl extends MatchableBoolean {
        private final boolean value;

        MatchableBooleanImpl(boolean value) {
            this.value = value;
        }

        @Override
        public boolean getValue() {
            return value;
        }
    }
}
