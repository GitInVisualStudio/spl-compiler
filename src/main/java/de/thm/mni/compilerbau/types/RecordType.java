package de.thm.mni.compilerbau.types;

import de.thm.mni.compilerbau.absyn.VariableDeclaration;
import java.util.List;
import java.util.stream.Collectors;

public class RecordType extends Type {

    public final List<VariableDeclaration> fields;

    public RecordType(List<VariableDeclaration> fields) {
        super(fields.stream().map(x -> x.typeExpression.dataType.byteSize).reduce(0, Integer::sum));
        this.fields = fields;
    }

    @Override
    public String toString() {
        return String.format("record {%s}", fields.stream().map(x -> x.toString()).collect(Collectors.joining()));
    }

    @Override
    public boolean equals(Object obj) {
        if (this  == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        RecordType other = (RecordType) obj;
        if (fields.size() != other.fields.size())
            return false;
        for (int i = 0; i < fields.size(); i++)
            if (!fields.get(i).equals(other.fields.get(i)))
                return false;
        return true;
    }
}
