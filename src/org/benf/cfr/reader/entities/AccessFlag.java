package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.entities.attributes.Attribute;
import org.benf.cfr.reader.entities.attributes.AttributeSynthetic;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public enum AccessFlag {
    ACC_PUBLIC("public"),
    ACC_PRIVATE("private"),
    ACC_PROTECTED("protected"),
    ACC_STATIC("static"),
    ACC_FINAL("final"),
    ACC_SUPER("super"),
    ACC_VOLATILE("volatile"),
    ACC_TRANSIENT("transient"),
    ACC_INTERFACE("interface"),
    ACC_ABSTRACT("abstract"),
    ACC_STRICT("strictfp"),     // inferred from constructors.
    ACC_SYNTHETIC("/* synthetic */"),
    ACC_ANNOTATION("/* annotation */"),
    ACC_ENUM("/* enum */");

    public final String name;

    private AccessFlag(String name) {
        this.name = name;
    }

    public static Set<AccessFlag> build(int raw) {
        Set<AccessFlag> res = new TreeSet<AccessFlag>();

        // Because we're decoding a C++ style enum.
        if (0 != (raw & 0x1)) res.add(ACC_PUBLIC);
        if (0 != (raw & 0x2)) res.add(ACC_PRIVATE);
        if (0 != (raw & 0x4)) res.add(ACC_PROTECTED);
        if (0 != (raw & 0x8)) res.add(ACC_STATIC);
        if (0 != (raw & 0x10)) res.add(ACC_FINAL);
        if (0 != (raw & 0x20)) res.add(ACC_SUPER);
        if (0 != (raw & 0x40)) res.add(ACC_VOLATILE);
        if (0 != (raw & 0x80)) res.add(ACC_TRANSIENT);
        if (0 != (raw & 0x200)) res.add(ACC_INTERFACE);
        if (0 != (raw & 0x400)) res.add(ACC_ABSTRACT);
        if (0 != (raw & 0x1000)) res.add(ACC_SYNTHETIC);
        if (0 != (raw & 0x2000)) res.add(ACC_ANNOTATION);
        if (0 != (raw & 0x4000)) res.add(ACC_ENUM);

        if (res.isEmpty()) return res;
        Set<AccessFlag> resaf = EnumSet.copyOf(res);
        return resaf;
    }

    @Override
    public String toString() {
        return name;
    }

    public static void applyAttributes(Map<String, Attribute> attributeMap, Set<AccessFlag> accessFlagSet) {
        if (attributeMap.containsKey(AttributeSynthetic.ATTRIBUTE_NAME)) {
            accessFlagSet.add(ACC_SYNTHETIC);
        }
    }
}
