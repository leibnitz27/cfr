package org.benf.cfr.reader.util;

import java.util.regex.Pattern;

public interface MiscConstants {
    String CFR_HEADER_BRA = "Decompiled with CFR";

    String INIT_METHOD = "<init>";
    String STATIC_INIT_METHOD = "<clinit>";
    String DOT_THIS = ".this";
    String THIS = "this";
    String CLASS = "class";
    String NEW = "new";
    String EQUALS = "equals";
    String HASHCODE = "hashCode";
    String TOSTRING = "toString";
    String PACKAGE_INFO = "package-info";

    String UNBOUND_GENERIC = "?";
    char INNER_CLASS_SEP_CHAR = '$';
    String INNER_CLASS_SEP_STR = "$";

    String DESERIALISE_LAMBDA_METHOD = "$deserializeLambda$";
    String SCALA_SERIAL_VERSION = "serialVersionUID";
    String GET_CLASS_NAME = "getClass";
    String REQUIRE_NON_NULL = "requireNonNull";

    String MANIFEST_PATH = "META-INF/MANIFEST.MF";
    String MULTI_RELEASE_KEY = "Multi-Release";
    String MULTI_RELEASE_PREFIX = "META-INF/versions/";
    String WAR_PREFIX = "WEB-INF/classes/";
    Pattern MULTI_RELEASE_PATH_PATTERN = Pattern.compile("^" + MULTI_RELEASE_PREFIX + "(\\d+)/(.*)$");
}
