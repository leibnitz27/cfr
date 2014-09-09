package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationAnonymousInner;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

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
