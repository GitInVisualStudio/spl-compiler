package de.thm.mni.compilerbau.absyn;

import de.thm.mni.compilerbau.absyn.visitor.Visitor;
import de.thm.mni.compilerbau.table.Identifier;

import java.util.List;

public final class RecordTypeExpression extends TypeExpression {

    public final List<VariableDeclaration> fields;

    public RecordTypeExpression(Position position, List<VariableDeclaration> fields) {
        super(position);
        this.fields = fields;
    }

    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return formatAst("RecordTypeExpression", fields.toArray());
    }
}
