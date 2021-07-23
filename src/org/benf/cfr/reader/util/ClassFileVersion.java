package org.benf.cfr.reader.util;

import org.benf.cfr.reader.util.collections.MapFactory;

import java.util.Map;

@SuppressWarnings("unused")
public class ClassFileVersion {
    private final int major;
    private final int minor;
    private final String name;
    private final static Map<String, ClassFileVersion> byName = MapFactory.newOrderedMap();

    public ClassFileVersion(int major, int minor) {
        this(major, minor, null);
    }

    private ClassFileVersion(int major, int minor, String name) {
        this.major = major;
        this.minor = minor;
        this.name = name;
        if (name != null) {
            byName.put("j" + name + (minor == 65535 ? "pre" : ""), this);
        }
    }

    public static ClassFileVersion parse(String arg) {
        ClassFileVersion named = byName.get(arg);
        if (named != null) return named;
        String[] parts = arg.split("\\.",2);
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
            return new ClassFileVersion(major, minor);
        } catch (Exception e) {
            throw new ConfusedCFRException("Can't parse classfile version " + arg);
        }
    }

    public boolean equalOrLater(ClassFileVersion other) {
        if (this.major < other.major) return false;
        if (this.major > other.major) return true;
        if (this.minor < other.minor) return false;
        return true;
    }

    public boolean isExperimental() {
        return this.minor == 65535;
    }

    public boolean sameMajor(ClassFileVersion other) {
        return major == other.major;
    }

    public boolean before(ClassFileVersion other) {
        return !equalOrLater(other);
    }

    public static Map<String, ClassFileVersion> getByName() {
        return byName;
    }

    @Override
    public String toString() {
        return "" + major + "." + minor + (name == null ? "" : (" (Java " + name + ")")) + (minor == 65535 ? " preview" : "");
    }

    public static ClassFileVersion JAVA_1_0 = new ClassFileVersion(45, 3, "1.0");
    public static ClassFileVersion JAVA_1_2 = new ClassFileVersion(46, 0, "1.2");
    public static ClassFileVersion JAVA_1_3 = new ClassFileVersion(47, 0, "1.3");
    public static ClassFileVersion JAVA_1_4 = new ClassFileVersion(48, 0, "1.4");
    public static ClassFileVersion JAVA_5 = new ClassFileVersion(49, 0, "5");
    public static ClassFileVersion JAVA_6 = new ClassFileVersion(50, 0, "6");
    public static ClassFileVersion JAVA_7 = new ClassFileVersion(51, 0, "7");
    public static ClassFileVersion JAVA_8 = new ClassFileVersion(52, 0, "8");
    public static ClassFileVersion JAVA_9 = new ClassFileVersion(53, 0, "9");
    public static ClassFileVersion JAVA_10 = new ClassFileVersion(54, 0, "10");
    public static ClassFileVersion JAVA_11 = new ClassFileVersion(55, 0, "11");
    public static ClassFileVersion JAVA_12 = new ClassFileVersion(56, 0, "12");
    public static ClassFileVersion JAVA_12_Experimental = new ClassFileVersion(56, 65535, "12");
    public static ClassFileVersion JAVA_13 = new ClassFileVersion(57, 0, "13");
    public static ClassFileVersion JAVA_14 = new ClassFileVersion(58, 0, "14");
    public static ClassFileVersion JAVA_14_Experimental = new ClassFileVersion(58, 65535, "14");
    public static ClassFileVersion JAVA_15 = new ClassFileVersion(59, 0, "15");
    public static ClassFileVersion JAVA_16 = new ClassFileVersion(60, 0, "16");
    public static ClassFileVersion JAVA_16_Experimental = new ClassFileVersion(60, 65535, "16");
    public static ClassFileVersion JAVA_17 = new ClassFileVersion(61, 0, "17");
    public static ClassFileVersion JAVA_17_Experimental = new ClassFileVersion(61, 65535, "17");
    public static ClassFileVersion JAVA_18 = new ClassFileVersion(62, 0, "18");
    public static ClassFileVersion JAVA_18_Experimental = new ClassFileVersion(62, 65535, "18");
}
