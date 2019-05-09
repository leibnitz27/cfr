package org.benf.cfr.reader.util;

public interface MiscConstants {
    class Version {
        private static String version;

        static String getVersion() {
            if (version != null) return version;
            try {
                version = Version.class.getPackage().getImplementationVersion();
            } catch (Exception ignore) {
                //
            }
            if (version == null) {
                version = "<Could not determine version>";
            }
            return version;
        }
    }

    String CFR_VERSION = Version.getVersion();

    String CFR_HEADER_BRA = "Decompiled with CFR";

    String INIT_METHOD = "<init>";
    String STATIC_INIT_METHOD = "<clinit>";
    String THIS = "this";
    String CLASS = "class";
    String UNBOUND_GENERIC = "?";
    char INNER_CLASS_SEP_CHAR = '$';
    String INNER_CLASS_SEP_STR = "$";

    String DESERIALISE_LAMBDA_METHOD = "$deserializeLambda$";
    String SCALA_SERIAL_VERSION = "serialVersionUID";
    String GET_CLASS_NAME = "getClass";
    String REQUIRE_NON_NULL = "requireNonNull";
}
