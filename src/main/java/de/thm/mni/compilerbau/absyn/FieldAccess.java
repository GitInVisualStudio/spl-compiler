package de.thm.mni.compilerbau.absyn;

import de.thm.mni.compilerbau.absyn.visitor.Visitor;
import de.thm.mni.compilerbau.table.Identifier;

public final class FieldAccess extends Variable {

    final public Variable variable;
    final public Identifier field;

    public FieldAccess(Position position, Variable variable, Identifier field) {
        super(position);
        this.variable = variable;
        this.field = field;
    }

    @Override
    public void accept(Visitor visitor) {visitor.visit(this);}

    @Override
    public String toString() {
        return formatAst("FieldAccess", variable, field);
    }
}
