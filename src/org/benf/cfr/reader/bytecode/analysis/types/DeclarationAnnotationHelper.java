package org.benf.cfr.reader.bytecode.analysis.types;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.benf.cfr.reader.entities.annotations.AnnotationTableEntry;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.attributes.TypePathPart;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.ListFactory;

public class DeclarationAnnotationHelper {
    private DeclarationAnnotationHelper() { }

    private static final DecompilerComments EMPTY_DECOMPILER_COMMENTS = new DecompilerComments();

    /**
     * Represents information about where to place declaration and {@link ElementType#TYPE_USE}
     * annotations for a place where both can occur.
     *
     * <p>This information differentiates between admissible and non-admissible types. What this
     * means is defined in <a href="https://docs.oracle.com/javase/specs/jls/se13/html/jls-9.html#jls-9.7.4-400">JLS 9.7.4</a>.
     * Depending on whether a type is admissible or not annotation placement has to be adjusted.
     *
     * <p>Examples for annotation {@code @DeclTypeUse} with target {@code TYPE_USE} and {@code FIELD}:
     * <pre>
     * // Applies both to declaration and type; placement in front of type is admissible
     * &#64;TypeUse
     * public String f;
     *
     * // Would consider the annotation repeated
     * &#64;TypeUse
     * public &#64;TypeUse String f;
     *
     * // Applies only to type
     * public java.lang.&#64;TypeUse String f;
     *
     * // Applies both to declaration and type
     * // Probably javac bug (JDK-8223936) and should only apply to declaration
     * &#64;TypeUse
     * public java.lang.String f;
     * </pre>
     *
     * <p>So depending on how the type is written by the decompiler (maybe the qualified name
     * is used to resolve type collisions) the annotations have to be placed differently.
     * Additionally the annotations present in the class files can
     * {@linkplain #requiresNonAdmissibleType() require creating a non-admissible type}, e.g.
     * because the annotation is only present on the type, but not on the declaration, or
     * because it was different values or appears in a different order.
     */
    public static class DeclarationAnnotationsInfo {
        private final List<AnnotationTableEntry> declAnnotationsAdmissible;
        private final List<AnnotationTableEntry> declAnnotationsNonAdmissible;
        private final List<AnnotationTableTypeEntry> typeAnnotationsAdmissible;
        private final List<AnnotationTableTypeEntry> typeAnnotationsNonAdmissible;
        private final boolean requiresNonAdmissibleType;

        private DeclarationAnnotationsInfo(List<AnnotationTableEntry> declAnnotationsAdmissible, List<AnnotationTableEntry> declAnnotationsNonAdmissible, List<AnnotationTableTypeEntry> typeAnnotationsAdmissible, List<AnnotationTableTypeEntry> typeAnnotationsNonAdmissible, boolean requiresNonAdmissibleType) {
            this.declAnnotationsAdmissible = declAnnotationsAdmissible;
            this.declAnnotationsNonAdmissible = declAnnotationsNonAdmissible;
            this.typeAnnotationsAdmissible = typeAnnotationsAdmissible;
            this.typeAnnotationsNonAdmissible = typeAnnotationsNonAdmissible;
            this.requiresNonAdmissibleType = requiresNonAdmissibleType;
        }

        private static DeclarationAnnotationsInfo possibleAdmissible(List<AnnotationTableEntry> declAnnotations, List<AnnotationTableTypeEntry> typeAnnotations) {
            return new DeclarationAnnotationsInfo(declAnnotations, declAnnotations, typeAnnotations, typeAnnotations, false);
        }

        private static DeclarationAnnotationsInfo possibleAdmissible(List<AnnotationTableEntry> declAnnotationsAdmissible, List<AnnotationTableEntry> declAnnotationsNonAdmissible, List<AnnotationTableTypeEntry> typeAnnotationsAdmissible, List<AnnotationTableTypeEntry> typeAnnotationsNonAdmissible) {
            return new DeclarationAnnotationsInfo(declAnnotationsAdmissible, declAnnotationsNonAdmissible, typeAnnotationsAdmissible, typeAnnotationsNonAdmissible, false);
        }

        private static DeclarationAnnotationsInfo requiringNonAdmissible(List<AnnotationTableEntry> declAnnotations, List<AnnotationTableTypeEntry> typeAnnotations) {
            return new DeclarationAnnotationsInfo(null, declAnnotations, null, typeAnnotations, true);
        }

        /**
         * @return
         *      Whether the type has to be written in a way that makes placing {@code TYPE_USE} annotations
         *      in front of it non-admissible, e.g. using the qualified name or qualifying it using a
         *      non-admissible outer class ({@code StaticOuter.StaticInner f;})
         */
        public boolean requiresNonAdmissibleType() {
            return requiresNonAdmissibleType;
        }

        private void checkCanProvideAnnotations(boolean usesAdmissibleType) {
            if (usesAdmissibleType && requiresNonAdmissibleType()) {
                throw new IllegalArgumentException("Can only provide annotations if non-admissible type is used");
            }
        }

        /**
         * @param usesAdmissibleType
         *      Whether the caller writes the type in a way that makes placing {@code TYPE_USE}
         *      annotations in front of it admissible
         * @return
         *      Annotations to place on the declaration
         * @throws IllegalArgumentException
         *      If annotations for an admissible type are requested, but this placement information
         *      {@linkplain #requiresNonAdmissibleType() does not support it}
         */
        public List<AnnotationTableEntry> getDeclarationAnnotations(boolean usesAdmissibleType) {
            checkCanProvideAnnotations(usesAdmissibleType);
            return usesAdmissibleType ? declAnnotationsAdmissible : declAnnotationsNonAdmissible;
        }

        /**
         * @param usesAdmissibleType
         *      Whether the caller writes the type in a way that makes placing {@code TYPE_USE}
         *      annotations in front of it admissible
         * @return
         *      Annotations to place on the type
         * @throws IllegalArgumentException
         *      If annotations for an admissible type are requested, but this placement information
         *      {@linkplain #requiresNonAdmissibleType() does not support it}
         */
        public List<AnnotationTableTypeEntry> getTypeAnnotations(boolean usesAdmissibleType) {
            checkCanProvideAnnotations(usesAdmissibleType);
            return usesAdmissibleType ? typeAnnotationsAdmissible : typeAnnotationsNonAdmissible;
        }
    }

    private static class SinglePartTypeIterator implements JavaAnnotatedTypeIterator {
        protected boolean wasOtherTypeUsed = false;

        @Override
        public JavaAnnotatedTypeIterator moveArray(DecompilerComments comments) {
            wasOtherTypeUsed = true;
            return this;
        }

        @Override
        public JavaAnnotatedTypeIterator moveBound(DecompilerComments comments) {
            wasOtherTypeUsed = true;
            return this;
        }

        @Override
        public JavaAnnotatedTypeIterator moveNested(DecompilerComments comments) {
            wasOtherTypeUsed = true;
            return this;
        }

        @Override
        public JavaAnnotatedTypeIterator moveParameterized(int index, DecompilerComments comments) {
            wasOtherTypeUsed = true;
            return this;
        }

        @Override
        public void apply(AnnotationTableEntry entry) { }
    }

    private static class NestedCountingIterator extends SinglePartTypeIterator {
        private int nestingLevel = 0;

        @Override
        public JavaAnnotatedTypeIterator moveNested(DecompilerComments comments) {
            nestingLevel++;
            return this;
        }
    }

    private static class ArrayCountingIterator extends SinglePartTypeIterator {
        private int componentLevel = 0;

        @Override
        public JavaAnnotatedTypeIterator moveArray(DecompilerComments comments) {
            componentLevel++;
            return this;
        }
    }

    private static Set<JavaTypeInstance> getDeclAndTypeUseAnnotationTypes(List<AnnotationTableEntry> declAnnotations, List<AnnotationTableTypeEntry> typeAnnotations) {
        /*
         * TODO: To be more correct the @Target meta annotation on the annotation
         * types would have to be checked, however that is currently not easily
         * possible.
         * Therefore at least find annotation types which are actually used on
         * both declaration and type.
         */
        Set<JavaTypeInstance> declTypeAnnotations = new HashSet<JavaTypeInstance>();
        for (AnnotationTableEntry declAnn : declAnnotations) {
            declTypeAnnotations.add(declAnn.getClazz());
        }
        List<JavaTypeInstance> typeAnnotationClasses = new ArrayList<JavaTypeInstance>();
        for (AnnotationTableTypeEntry typeAnn : typeAnnotations) {
            typeAnnotationClasses.add(typeAnn.getClazz());
        }
        declTypeAnnotations.retainAll(typeAnnotationClasses);

        return declTypeAnnotations;
    }

    /**
     * Returns for the annotations the common inner class annotation index, or {@code null}
     * if there is no common index. For example with {@code A} and {@code B} being inner classes:
     * <pre>{@code
     * @TypeUse1 @TypeUse2 A.B f; // Common index: 0
     * A. @TypeUse1 @TypeUse2 B f; // Common index: 1
     * @TypeUse1 A.@TypeUse2 B f; // No common index
     * }</pre>
     *
     * @param typeAnnotations
     *      For which the common inner annotation index should be determined
     * @return
     *      Common annotation index or {@code null}
     */
    private static Integer getCommonInnerClassAnnotationIndex(List<AnnotationTableTypeEntry> typeAnnotations) {
        Integer commonIndex = null;

        for (AnnotationTableTypeEntry annotation : typeAnnotations) {
            NestedCountingIterator annotationIterator = new NestedCountingIterator();

            for (TypePathPart typePathPart : annotation.getTypePath().segments) {
                typePathPart.apply(annotationIterator, EMPTY_DECOMPILER_COMMENTS);

                if (annotationIterator.wasOtherTypeUsed) {
                    break;
                }
            }

            if (commonIndex == null) {
                commonIndex = annotationIterator.nestingLevel;
            }
            else if (commonIndex != annotationIterator.nestingLevel) {
                return null;
            }
        }

        return commonIndex;
    }

    /**
     * Returns whether the type annotation can be moved to the declaration.
     * For example:
     * <pre>{@code
     * // Can be moved
     * public @TypeUse String[] f;
     * // when moved:
     * @TypeUse
     * public String[] f;
     *
     * // Cannot be moved
     * public String @TypeUse[] f;
     * }</pre>
     *
     * The common inner annotation index is used to be not too restrictive if
     * the outer class has no annotations and therefore the type annotation
     * could be moved (relies on {@link JavaRefTypeInstance} to only refer to
     * the first annotated type). For example with {@code A} and {@code B} being
     * inner classes:
     * <pre>{@code
     * public @TypeUse A.@TypeUse B f; // Not movable
     *
     * // Can be moved if B is not qualified using outer class
     * public @TypeUse1 @TypeUse2 B f;
     * }</pre>
     *
     * @param annotatedType
     *      Type to be annotated
     * @param typeAnnotation
     *      Annotation for the type
     * @param commonInnerAnnotationIndex
     *      Nullable index of the common annotation position for inner classes
     * @return
     *      Whether the annotation can be moved to the declaration
     */
    // This assumes that JDK-8223936 is fixed and would only apply the annotation to the
    // declaration, but not the type
    private static boolean canTypeAnnotationBeMovedToDecl(JavaTypeInstance annotatedType, AnnotationTableTypeEntry typeAnnotation, Integer commonInnerAnnotationIndex) {
        List<TypePathPart> typePathParts = typeAnnotation.getTypePath().segments;

        if (annotatedType.getInnerClassHereInfo().isInnerClass()) {
            NestedCountingIterator annotationIterator = new NestedCountingIterator();

            for (TypePathPart typePathPart : typePathParts) {
                typePathPart.apply(annotationIterator, EMPTY_DECOMPILER_COMMENTS);

                if (annotationIterator.wasOtherTypeUsed) {
                    return false;
                }
            }

            int nestingLevel = annotationIterator.nestingLevel;
            return nestingLevel == 0 || commonInnerAnnotationIndex != null && nestingLevel == commonInnerAnnotationIndex;
        }
        else if (annotatedType.getNumArrayDimensions() > 0) {
            ArrayCountingIterator annotationIterator = new ArrayCountingIterator();
            for (TypePathPart typePathPart : typePathParts) {
                typePathPart.apply(annotationIterator, EMPTY_DECOMPILER_COMMENTS);

                if (annotationIterator.wasOtherTypeUsed) {
                    return false;
                }
            }

            // Return true only if array component type is annotated: @TypeUse String[]
            // Any other value would mean that array itself or nested array is annotated: String @TypeUse []
            return annotationIterator.componentLevel == annotatedType.getNumArrayDimensions();
        }
        else {
            // Arrays are checked before because there empty parts list would not annotate component type
            return typePathParts.isEmpty();
        }
    }

    private static boolean areAnnotationsEqual(List<AnnotationTableEntry> declAnnotations, List<AnnotationTableTypeEntry> typeAnnotations) {
        if (declAnnotations.size() != typeAnnotations.size()) {
            return false;
        }

        for (int i = 0; i < declAnnotations.size(); i++) {
            if (!declAnnotations.get(i).isAnnotationEqual(typeAnnotations.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Calculates information about where to place declaration and {@link ElementType#TYPE_USE}
     * annotations for a place where both can occur.
     *
     * @param nullableAnnotatedType
     *      Type for which the {@code TYPE_USE} annotations apply, {@code null} if there is no
     *      type (e.g. for constructor declarations)
     * @param declarationAnnotations
     *      Annotations for the declaration, e.g. {@link ElementType#FIELD}
     * @param typeAnnotations
     *      Annotations for the type, i.e. {@link ElementType#TYPE_USE}
     * @return
     *      Information about how to place the annotations
     */
    public static DeclarationAnnotationsInfo getDeclarationInfo(JavaTypeInstance nullableAnnotatedType, List<AnnotationTableEntry> declarationAnnotations, List<AnnotationTableTypeEntry> typeAnnotations) {
        if (declarationAnnotations == null || declarationAnnotations.isEmpty() || typeAnnotations == null || typeAnnotations.isEmpty()) {
            return DeclarationAnnotationsInfo.possibleAdmissible(ListFactory.orEmptyList(declarationAnnotations), ListFactory.orEmptyList(typeAnnotations));
        }

        /*
         * Set containing all annotation classes which can apply both to the declaration
         * and type (TYPE_USE).
         */
        Set<JavaTypeInstance> declTypeAnnotations = getDeclAndTypeUseAnnotationTypes(declarationAnnotations, typeAnnotations);

        // No need to perform further checks if all annotations apply either to
        // the declaration or type
        if (declTypeAnnotations.isEmpty()) {
            return DeclarationAnnotationsInfo.possibleAdmissible(declarationAnnotations, typeAnnotations);
        }

        Integer commonInnerAnnotationIndex = getCommonInnerClassAnnotationIndex(typeAnnotations);
        List<AnnotationTableTypeEntry> typeDeclTypeAnnotations = new ArrayList<AnnotationTableTypeEntry>();
        boolean requiresMoveToTypeAnn = false;
        // Index of the first decl + TYPE_USE annotation which must be placed on the
        // type due to ordering constraints; only relevant if requiresMoveToTypeAnn == true
        int firstMovedTypeAnnIndex = -1;

        for (AnnotationTableTypeEntry typeAnn : typeAnnotations) {
            if (declTypeAnnotations.contains(typeAnn.getClazz())) {
                // Check if type annotation can be moved to declaration
                if (firstMovedTypeAnnIndex != -1 && nullableAnnotatedType != null && !canTypeAnnotationBeMovedToDecl(nullableAnnotatedType, typeAnn, commonInnerAnnotationIndex)) {
                    firstMovedTypeAnnIndex = typeDeclTypeAnnotations.size();
                }

                if (firstMovedTypeAnnIndex != -1) {
                    requiresMoveToTypeAnn = true;
                }

                typeDeclTypeAnnotations.add(typeAnn);
            }
            else if (firstMovedTypeAnnIndex == -1) {
                firstMovedTypeAnnIndex = typeDeclTypeAnnotations.size();
            }
        }

        // Index of last decl + TYPE_USE annotation which must be placed on the
        // declaration due to ordering constraints
        int lastNonMovableDeclAnnIndex = -1;
        List<AnnotationTableEntry> declDeclTypeAnnotations = new ArrayList<AnnotationTableEntry>();
        for (AnnotationTableEntry declAnn : declarationAnnotations) {
            if (declTypeAnnotations.contains(declAnn.getClazz())) {
                declDeclTypeAnnotations.add(declAnn);
            }
            else {
                lastNonMovableDeclAnnIndex = declDeclTypeAnnotations.size() - 1;
            }
        }

        /*
         * If an annotation applicable to declaration and type MUST be moved to both
         * declaration and type to maintain correct annotation order then annotations
         * must be emitted twice:
         *   @TypeAndDecl
         *   @Decl
         *   java.lang.@Type @TypeAndDecl String f;
         * Note that this is currently not possible to compile due to javac bug JDK-8223936
         *
         * Or if numbers of annotations applicable to declaration and type do not match:
         *   java.lang.@TypeAndDecl String f; // Only type, but not declaration, is annotated
         */
        /*
         * Note that comparing lastNonMovableDeclAnnIndex and firstMovedTypeAnnIndex is fine
         * because it assumes that same amount of decl + TYPE_USE annotations exist for
         * declaration and for type, otherwise would require non-admissible type anyways.
         */
        boolean contradictingMoves = requiresMoveToTypeAnn && lastNonMovableDeclAnnIndex >= firstMovedTypeAnnIndex;
        if (contradictingMoves || typeDeclTypeAnnotations.size() != declDeclTypeAnnotations.size()) {
            return DeclarationAnnotationsInfo.requiringNonAdmissible(declarationAnnotations, typeAnnotations);
        }

        if (!areAnnotationsEqual(declDeclTypeAnnotations, typeDeclTypeAnnotations)) {
            return DeclarationAnnotationsInfo.requiringNonAdmissible(declarationAnnotations, typeAnnotations);
        }

        if (requiresMoveToTypeAnn) {
            /*
             * Move some annotations applicable to declaration and type to the type:
             *   @TypeAndDecl
             *   @Type @TypeAndDecl2 String f;
             *
             * Here TypeAndDecl2 must be moved to the type because it is placed after TYPE_USE
             * only annotation Type.
             */
            List<AnnotationTableEntry> declAnnotationsAdmissible = new ArrayList<AnnotationTableEntry>(declarationAnnotations);
            declAnnotationsAdmissible.removeAll(declDeclTypeAnnotations.subList(firstMovedTypeAnnIndex, declDeclTypeAnnotations.size()));
            // For annotations to place on type need to remove all other decl + TYPE_USE
            // annotations which remain on declaration
            List<AnnotationTableTypeEntry> typeAnnotationsAdmissible = new ArrayList<AnnotationTableTypeEntry>(typeAnnotations);
            typeAnnotationsAdmissible.removeAll(typeDeclTypeAnnotations.subList(0, firstMovedTypeAnnIndex));

            return DeclarationAnnotationsInfo.possibleAdmissible(declAnnotationsAdmissible, declarationAnnotations, typeAnnotationsAdmissible, typeAnnotations);
        }
        else {
            // Move all annotations applicable to declaration and type to the
            // declaration
            List<AnnotationTableTypeEntry> typeAnnotationsAdmissible = new ArrayList<AnnotationTableTypeEntry>(typeAnnotations);
            typeAnnotationsAdmissible.removeAll(typeDeclTypeAnnotations);

            return DeclarationAnnotationsInfo.possibleAdmissible(declarationAnnotations, declarationAnnotations, typeAnnotationsAdmissible, typeAnnotations);
        }
    }
}
