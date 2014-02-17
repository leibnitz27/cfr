package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.entities.attributes.Attribute;
import org.benf.cfr.reader.entities.attributes.AttributeSynthetic;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 17/04/2011
 */
public enum AccessFlagMethod {
    ACC_PUBLIC("public"),
    ACC_PRIVATE("private"),
    ACC_PROTECTED("protected"),
    ACC_STATIC("static"),
    ACC_FINAL("final"),
    ACC_SYNCHRONISED("synchronized"),
    ACC_BRIDGE("/* bridge */"),
    ACC_VARARGS("/* varargs */"),
    ACC_NATIVE("native"),
    ACC_ABSTRACT("abstract"),
    ACC_STRICT("strictfp"),
    ACC_SYNTHETIC("/* synthetic */");

    private final String name;

    private AccessFlagMethod(String name) {
        this.name = name;
    }

    public static EnumSet<AccessFlagMethod> build(int raw) {
        EnumSet<AccessFlagMethod> res = EnumSet.<AccessFlagMethod>noneOf(AccessFlagMethod.class);

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
        EnumSet<AccessFlagMethod> resaf = EnumSet.copyOf(res);
        return resaf;
    }


    @Override
    public String toString() {
        return name;
    }

    public static void applyAttributes(Map<String, Attribute> attributeMap, Set<AccessFlagMethod> accessFlagSet) {
        if (attributeMap.containsKey(AttributeSynthetic.ATTRIBUTE_NAME)) accessFlagSet.add(ACC_SYNTHETIC);
    }

}
