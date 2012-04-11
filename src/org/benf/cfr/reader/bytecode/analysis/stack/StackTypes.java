package org.benf.cfr.reader.bytecode.analysis.stack;

import org.benf.cfr.reader.util.ListFactory;

import java.util.ArrayList;
import java.util.Arrays;

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
}
