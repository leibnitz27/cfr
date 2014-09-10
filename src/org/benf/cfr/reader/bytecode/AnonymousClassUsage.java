package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationAnonymousInner;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

/*
 * Usage of anonymous classes currently requires decorating those classes once we've determined the code that's
 * using them - i.e. mutating state. We therefore have to extract this information so we don't perform it multiple
 * times (inside a recovery).
 */
public class AnonymousClassUsage {
    private final List<Pair<ClassFile, ConstructorInvokationAnonymousInner>> noted = ListFactory.newList();

    public void note(ClassFile classFile, ConstructorInvokationAnonymousInner constructorInvokationAnonymousInner) {
        noted.add(Pair.make(classFile, constructorInvokationAnonymousInner));
    }

    public void useNotes() {
        for (Pair<ClassFile, ConstructorInvokationAnonymousInner> note : noted) {
            note.getFirst().noteAnonymousUse(note.getSecond());
        }
    }
}
