package org.benf.cfr.reader.entities;

import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 17/04/2011
 * Time: 21:49
 * To change this template use File | Settings | File Templates.
 */
public enum AccessFlagMethod {
    ACC_PUBLIC,
    ACC_PRIVATE,
    ACC_PROTECTED,
    ACC_STATIC,
    ACC_FINAL,
    ACC_SYNCHRONISED,
    ACC_BRIDGE,
    ACC_VARARGS,
    ACC_NATIVE,
    ACC_ABSTRACT,
    ACC_STRICT,
    ACC_SYNTHETIC;

    public static Set<AccessFlagMethod> build(int raw)
    {
        Set<AccessFlagMethod> res = new TreeSet<AccessFlagMethod>();

        // Because we're decoding a C++ style enum.
        if (0 != (raw & 0x1)) res.add(ACC_PUBLIC);
        if (0 != (raw & 0x2)) res.add(ACC_PRIVATE);
        if (0 != (raw & 0x4)) res.add(ACC_PROTECTED);
        if (0 != (raw & 0x8)) res.add(ACC_STATIC);
        if (0 != (raw & 0x10)) res.add(ACC_FINAL);
        if (0 != (raw & 0x20)) res.add(ACC_SYNCHRONISED);
        if (0 != (raw & 0x40)) res.add(ACC_BRIDGE);
        if (0 != (raw & 0x80)) res.add(ACC_VARARGS);
        if (0 != (raw & 0x100)) res.add(ACC_NATIVE);
        if (0 != (raw & 0x400)) res.add(ACC_ABSTRACT);
        if (0 != (raw & 0x800)) res.add(ACC_STRICT);
        if (0 != (raw & 0x1000)) res.add(ACC_SYNTHETIC);

        if (res.isEmpty()) return res;
        Set<AccessFlagMethod> resaf = EnumSet.copyOf(res);
        return resaf;
    }

}
