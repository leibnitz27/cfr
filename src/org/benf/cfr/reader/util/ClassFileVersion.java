package org.benf.cfr.reader.util;

@SuppressWarnings("unused")
public class ClassFileVersion {
    private final int major;
    private final int minor;
    private final String name;

    public ClassFileVersion(int major, int minor) {
        this(major, minor, null);
    }

    public ClassFileVersion(int major, int minor, String name) {
        this.major = major;
        this.minor = minor;
        this.name = name;
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

    @Override
    public String toString() {
        return "" + major + "." + minor + (name == null ? "" : (" (Java " + name + ")"));
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
}
