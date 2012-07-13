package org.benf.cfr.reader.bytecode.analysis.types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created:
 * User: lee
 * <p/>
 * Really List<StackType> but for legibility, shortened.
 */
public class StackTypes extends ArrayList<StackType> {
    public static final StackTypes EMPTY = new StackTypes();

    public StackTypes(StackType... stackTypes) {
        super(Arrays.asList(stackTypes));
    }

    public StackTypes(List<StackType> stackTypes) {
        super(stackTypes);
    }
}
