package de.thm.mni.compilerbau.phases._04a_tablebuild;

import de.thm.mni.compilerbau.CommandLineOptions;
import de.thm.mni.compilerbau.absyn.*;
import de.thm.mni.compilerbau.absyn.visitor.DoNothingVisitor;
import de.thm.mni.compilerbau.table.*;
import de.thm.mni.compilerbau.types.ArrayType;
import de.thm.mni.compilerbau.types.Type;
import de.thm.mni.compilerbau.utils.NotImplemented;
import de.thm.mni.compilerbau.utils.SplError;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This class is used to create and populate a {@link SymbolTable} containing entries for every symbol in the currently
 * compiled SPL program.
 * Every declaration of the SPL program needs its corresponding entry in the {@link SymbolTable}.
 * <p>
 * Calculated {@link Type}s can be stored in and read from the dataType field of the {@link Expression},
 * {@link TypeExpression} or {@link Variable} classes.
 */
public class TableBuilder extends DoNothingVisitor {
    private final CommandLineOptions options;
    private SymbolTable table;

    public TableBuilder(CommandLineOptions options) {
        this.table = TableInitializer.initializeGlobalTable(options);
        this.options = options;
    }

    public SymbolTable buildSymbolTable(Program program) {
        program.accept(this);

        Optional<GlobalDeclaration> main = program.declarations.stream().filter(x -> x.name.equals(new Identifier("main"))).findAny();

        if (main.isEmpty())
            throw SplError.MainIsMissing();

        if (!(main.get() instanceof ProcedureDeclaration))
            throw SplError.MainIsNotAProcedure();

        ProcedureDeclaration dec = (ProcedureDeclaration) main.get();
        if (dec.parameters.size() != 0)
            throw SplError.MainMustNotHaveParameters();

        return this.table;
    }

    @Override
    public void visit(Program program) {
        program.declarations.forEach(x -> x.accept(this));
    }

    @Override
    public void visit(ProcedureDeclaration procedureDeclaration) {
        SymbolTable global = this.table;
        table = new SymbolTable(this.table);

        procedureDeclaration.parameters.forEach(para -> para.accept(this));
        procedureDeclaration.variables.forEach(var -> var.accept(this));

        List<ParameterType> parameterTypes = procedureDeclaration.parameters.stream().map(x -> new ParameterType(x.typeExpression.dataType, x.isReference)).toList();
        ProcedureEntry entry = new ProcedureEntry(table, parameterTypes);

        table = global;
        table.enter(procedureDeclaration.name, entry);

        if (options.phaseOption == options.phaseOption.TABLES)
            printSymbolTableAtEndOfProcedure(procedureDeclaration.name, entry);
    }

    @Override
    public void visit(ParameterDeclaration parameterDeclaration) {
        parameterDeclaration.typeExpression.accept(this);
        if (parameterDeclaration.typeExpression.dataType instanceof ArrayType && !parameterDeclaration.isReference)
            throw SplError.MustBeAReferenceParameter(parameterDeclaration.position, parameterDeclaration.name);
        table.enter(parameterDeclaration.name, new VariableEntry(parameterDeclaration.typeExpression.dataType, parameterDeclaration.isReference));
    }

    @Override
    public void visit(VariableDeclaration variableDeclaration) {
        variableDeclaration.typeExpression.accept(this);
        table.enter(variableDeclaration.name, new VariableEntry(variableDeclaration.typeExpression.dataType, false), SplError.RedeclarationAsVariable(variableDeclaration.position, variableDeclaration.name));
    }

    @Override
    public void visit(NamedTypeExpression namedTypeExpression) {
        Optional<Entry> result = table.find(namedTypeExpression.name);
        if (result.isEmpty())
            throw SplError.UndefinedType(namedTypeExpression.position, namedTypeExpression.name);
        Entry entry = result.get();
        if (!(entry instanceof TypeEntry))
            throw SplError.NotAType(namedTypeExpression.position, namedTypeExpression.name);
        namedTypeExpression.dataType = ((TypeEntry)entry).type;
    }

    @Override
    public void visit(ArrayTypeExpression arrayTypeExpression) {
        arrayTypeExpression.baseType.accept(this);
        arrayTypeExpression.dataType = new ArrayType(arrayTypeExpression.baseType.dataType, arrayTypeExpression.arraySize);
    }

    @Override
    public void visit(TypeDeclaration typeDeclaration) {
        if (!table.find(typeDeclaration.name).isEmpty())
            throw SplError.RedeclarationAsType(typeDeclaration.position, typeDeclaration.name);
        typeDeclaration.typeExpression.accept(this);
        table.enter(typeDeclaration.name, new TypeEntry(typeDeclaration.typeExpression.dataType), SplError.RedeclarationAsType(typeDeclaration.position, typeDeclaration.name));
    }

    /**
     * Prints the local symbol table of a procedure together with a heading-line
     * NOTE: You have to call this after completing the local table to support '--tables'.
     *
     * @param name  The name of the procedure
     * @param entry The entry of the procedure to print
     */
    static void printSymbolTableAtEndOfProcedure(Identifier name, ProcedureEntry entry) {
        System.out.format("Symbol table at end of procedure '%s':\n", name);
        System.out.println(entry.localTable.toString());
    }
}
