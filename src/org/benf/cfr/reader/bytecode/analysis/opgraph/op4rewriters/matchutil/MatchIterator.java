package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil;

import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;

import java.util.List;

public class MatchIterator<T> {
    private final List<T> data;
    private int idx;

    public MatchIterator(List<T> data) {
        this.data = data;
        this.idx = -1;
    }

    private MatchIterator(List<T> data, int idx) {
        this.data = data;
        this.idx = idx;
    }

    public T getCurrent() {
        if (idx < 0) throw new IllegalStateException("Accessed before being advanced.");
        if (idx >= data.size()) {
            throw new IllegalStateException("Out of range");
        }
        return data.get(idx);
    }

    public MatchIterator<T> copy() {
        return new MatchIterator<T>(data, idx);
    }

    void advanceTo(MatchIterator<StructuredStatement> other) {
        if (data != other.data) throw new IllegalStateException(); // ref check.
        this.idx = other.idx;
    }

    public boolean hasNext() {
        return data!=null && idx < data.size() - 1;
    }

    private boolean isFinished() {
        return data!=null && idx >= data.size();
    }

    public boolean advance() {
        if (!isFinished()) idx++;
        return !isFinished();
    }

    public void rewind1() {
        if (idx > 0) idx--;
    }

    @Override
    public String toString() {
        if (isFinished()) return "Finished";
        /*T t = data.get(idx);When the advance() method has not been called,
            idx=-1,ArrayIndexOutOfBoundsException.   e.g:IDEA in Debug mode,
            Variable'view will call toString().so fix this bug ,and Enriched
            the method
         */

        /* when data=null. Constructor‘s parameter is 'ListFactory.newList()',
            At present, the use of it in the code will not cause
            the NullPointerException.
            But it's used elsewhere,that parameter is null,NullPointerException Will appear
         */
        if(data != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i <= data.size() - 1; i++) {
                if (i == 0) sb.append("data:[");
                //data's element
                T t = data.get(i);
                //SimpleName+obj's hashCode
                sb.append(t.getClass().getSimpleName()).append("@").append(Integer.toHexString(t.hashCode()));
                //Data traversal complete
                if (i == data.size() - 1) {
                    sb.append("]\n");//Line feed
                    //When the traversal is completed, explain to idx
                    if (idx == -1) {
                        sb.append("Accessed before being advanced:idx=-1");
                    } else {
                        sb.append("Current:idx=").append(idx);
                    }
                    //return
                    return sb.toString();
                } else {
                    //Traversal is not complete, data element separate with ','
                    sb.append(",");
                }
            }

        }else{
            return null;
        }
        return null;
    }

    public void rewind() {
        idx = 0;
    }
}
