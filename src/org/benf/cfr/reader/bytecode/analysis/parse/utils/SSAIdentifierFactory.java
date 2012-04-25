package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.util.MapFactory;

import java.util.Map;

/**
 * Created:
 * User: lee
 * Date: 24/04/2012
 */
public class SSAIdentifierFactory {
    private int counter = 0;

    public int getIdent(LValue lValue) {
        return counter++;
    }
}
