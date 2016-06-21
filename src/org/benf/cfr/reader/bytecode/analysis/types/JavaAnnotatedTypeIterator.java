package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.util.DecompilerComment;
import org.benf.cfr.reader.util.DecompilerComments;

// Implementations of this which end up in the wrong place return themselves, which is wrong, but tolerable.
public interface JavaAnnotatedTypeIterator {
    JavaAnnotatedTypeIterator moveArray(DecompilerComments comments);
    JavaAnnotatedTypeIterator moveBound(DecompilerComments comments);
    JavaAnnotatedTypeIterator moveNested(DecompilerComments comments);
    JavaAnnotatedTypeIterator moveParameterized(int index, DecompilerComments comments);
    void apply(AnnotationTableTypeEntry entry);

    public abstract class BaseAnnotatedTypeIterator implements JavaAnnotatedTypeIterator {
        protected void addBadComment(DecompilerComments comments) {
            // If we use this for more, might have to pass in a lambda instead.
            comments.addComment(DecompilerComment.BAD_ANNOTATION);
        }

        @Override
        public JavaAnnotatedTypeIterator moveParameterized(int index, DecompilerComments comments) {
            addBadComment(comments);
            return this;
        }

        @Override
        public JavaAnnotatedTypeIterator moveNested(DecompilerComments comments) {
            addBadComment(comments);
            return this;
        }

        @Override
        public JavaAnnotatedTypeIterator moveBound(DecompilerComments comments) {
            addBadComment(comments);
            return this;
        }

        @Override
        public JavaAnnotatedTypeIterator moveArray(DecompilerComments comments) {
            addBadComment(comments);
            return this;
        }
    }
}
