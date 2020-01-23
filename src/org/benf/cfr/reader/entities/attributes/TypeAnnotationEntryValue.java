package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.util.collections.MapFactory;

import java.util.Map;

// see JVMS Table 4.7.20-A. Interpretation of target_type values
public enum TypeAnnotationEntryValue {
    type_generic_class_interface(0x0, TypeAnnotationEntryKind.type_parameter_target, TypeAnnotationLocation.ClassFile),
    type_generic_method_constructor(0x1, TypeAnnotationEntryKind.type_parameter_target, TypeAnnotationLocation.method_info),
    type_extends_implements(0x10, TypeAnnotationEntryKind.supertype_target, TypeAnnotationLocation.ClassFile),
    type_type_parameter_class_interface(0x11, TypeAnnotationEntryKind.type_parameter_bound_target, TypeAnnotationLocation.ClassFile),
    type_type_parameter_method_constructor(0x12, TypeAnnotationEntryKind.type_parameter_bound_target, TypeAnnotationLocation.method_info),
    type_field(0x13, TypeAnnotationEntryKind.empty_target, TypeAnnotationLocation.field_info),
    type_ret_or_new(0x14, TypeAnnotationEntryKind.empty_target, TypeAnnotationLocation.method_info),
    type_receiver(0x15, TypeAnnotationEntryKind.empty_target, TypeAnnotationLocation.method_info),
    type_formal(0x16, TypeAnnotationEntryKind.method_formal_parameter_target, TypeAnnotationLocation.method_info),
    type_throws(0x17, TypeAnnotationEntryKind.throws_target, TypeAnnotationLocation.method_info),
    type_localvar(0x40, TypeAnnotationEntryKind.localvar_target, TypeAnnotationLocation.Code),
    type_resourcevar(0x41, TypeAnnotationEntryKind.localvar_target, TypeAnnotationLocation.Code),
    type_exceptionparameter(0x42, TypeAnnotationEntryKind.catch_target, TypeAnnotationLocation.Code),
    type_instanceof(0x43, TypeAnnotationEntryKind.offset_target, TypeAnnotationLocation.Code),
    type_new(0x44, TypeAnnotationEntryKind.offset_target, TypeAnnotationLocation.Code),
    type_methodrefnew(0x45, TypeAnnotationEntryKind.offset_target, TypeAnnotationLocation.Code),
    type_methodrefident(0x46, TypeAnnotationEntryKind.offset_target, TypeAnnotationLocation.Code),
    type_cast(0x47, TypeAnnotationEntryKind.type_argument_target, TypeAnnotationLocation.Code),
    type_generic_cons_new(0x48, TypeAnnotationEntryKind.type_argument_target, TypeAnnotationLocation.Code),
    type_generic_methodinvoke(0x49, TypeAnnotationEntryKind.type_argument_target, TypeAnnotationLocation.Code),
    type_generic_cons_methodrefnew(0x4a, TypeAnnotationEntryKind.type_argument_target, TypeAnnotationLocation.Code),
    type_generic_methodrefident(0x4b, TypeAnnotationEntryKind.type_argument_target, TypeAnnotationLocation.Code);

    private short value;
    private TypeAnnotationEntryKind type_parameter_target;
    private TypeAnnotationLocation location;

    private static final Map<Short, TypeAnnotationEntryValue> lut = MapFactory.newMap();

    TypeAnnotationEntryValue(int value, TypeAnnotationEntryKind type_parameter_target, TypeAnnotationLocation location) {
        this.value = (short)value;
        this.type_parameter_target = type_parameter_target;
        this.location = location;
    }

    public TypeAnnotationEntryKind getKind() {
        return type_parameter_target;
    }

    public TypeAnnotationLocation getLocation() {
        return location;
    }

    static {
        for (TypeAnnotationEntryValue value : values()) {
            lut.put(value.value, value);
        }
    }

    public static TypeAnnotationEntryValue get(short value) {
        TypeAnnotationEntryValue res = lut.get(value);
        if (res != null) {
            return res;
        }
        // Shouldn't happen - fallback for compatibility.
        throw new BadAttributeException();
    }
}
