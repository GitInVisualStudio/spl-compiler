package de.thm.mni.compilerbau.phases._06_codegen;

import de.thm.mni.compilerbau.CommandLineOptions;
import de.thm.mni.compilerbau.absyn.*;
import de.thm.mni.compilerbau.absyn.visitor.DoNothingVisitor;
import de.thm.mni.compilerbau.phases._05_varalloc.StackLayout;
import de.thm.mni.compilerbau.table.ParameterType;
import de.thm.mni.compilerbau.table.ProcedureEntry;
import de.thm.mni.compilerbau.table.SymbolTable;
import de.thm.mni.compilerbau.table.VariableEntry;
import de.thm.mni.compilerbau.types.ArrayType;
import de.thm.mni.compilerbau.types.RecordType;
import de.thm.mni.compilerbau.utils.SplError;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * This class is used to generate the assembly code for the compiled program.
 * This code is emitted via the {@link CodePrinter} in the output field of this class.
 */
public class CodeGenerator extends DoNothingVisitor {
    final CommandLineOptions options;
    final CodePrinter output;
    private SymbolTable table;
    private Register register;

    private static Register regNull = new Register(0),
                regFP = new Register(25),
                regSP = new Register(29),
                regRET = new Register(31);

    private int label;
    private boolean shouldLoad;

    /**
     * Initializes the code generator.
     *
     * @param options The command line options passed to the compiler
     * @param output  The PrintWriter to the output file.
     */
    public CodeGenerator(CommandLineOptions options, PrintWriter output) throws IOException {
        this.options = options;
        this.output = new CodePrinter(output);
        this.register = new Register(8);
        this.label = -1;
        this.shouldLoad = true;
    }

    public void generateCode(Program program, SymbolTable table) {
        assemblerProlog();
        this.table = table;
        program.accept(this);
    }

    private Register pushReg() {
        this.register = register.next();
        if (!register.isFreeUse())
            throw SplError.RegisterOverflow();
        return register;
    }

    private Register popReg() {
        return this.register = register.minus(1);
    }

    @Override
    public void visit(Program program) {
        program.declarations.forEach(x -> x.accept(this));
    }

    @Override
    public void visit(UnaryExpression unaryExpression) {
        unaryExpression.operand.accept(this);
        output.emitInstruction("sub", register, regNull, register);
    }

    @Override
    public void visit(CompoundStatement compoundStatement) {
        compoundStatement.statements.forEach(x -> x.accept(this));
    }

    @Override
    public void visit(WhileStatement whileStatement) {
        // while
        int whileLabel = ++label;
        int endLabel = ++label;

        output.emitLabel(String.format("L%d", whileLabel));
        whileStatement.condition.accept(this);
        whileStatement.body.accept(this);
        output.emitInstruction("j", String.format("L%d", whileLabel));
        // end of while
        output.emitLabel(String.format("L%d", endLabel));
    }

    @Override
    public void visit(IfStatement ifStatement) {
        int elseLabel = 0;
        int endLabel = 0;
        if (!(ifStatement.elsePart instanceof EmptyStatement))
            elseLabel = ++label;
        else
            endLabel = ++label;
        ifStatement.condition.accept(this);
        ifStatement.thenPart.accept(this);

        if (!(ifStatement.elsePart instanceof EmptyStatement)) {
            endLabel = ++label;
            output.emitInstruction("j", String.format("L%d", endLabel));
            output.emitLabel(String.format("L%d", elseLabel));
            ifStatement.elsePart.accept(this);
        }
        output.emitLabel(String.format("L%d", endLabel));
    }

    @Override
    public void visit(ProcedureDeclaration procedureDeclaration) {
        output.emitExport(procedureDeclaration.name.toString());
        output.emitLabel(procedureDeclaration.name.toString());

        ProcedureEntry entry = (ProcedureEntry) table.lookup(procedureDeclaration.name);

        StackLayout layout = entry.stackLayout;
        SymbolTable globalTable = table;

        table = entry.localTable;

        output.emitInstruction("sub", regSP, regSP, layout.frameSize(), "allocate frame");
        output.emitInstruction("stw", regFP, regSP, layout.oldFramePointerOffset(), "save old frame pointer");
        output.emitInstruction("add", regFP, regSP, layout.frameSize(), "setup new frame pointer");
        output.emitInstruction("stw", regRET, regFP, layout.oldReturnAddressOffset(), "save return register");

        procedureDeclaration.body.forEach(x -> x.accept(this));

        output.emitInstruction("ldw", regRET, regFP, layout.oldReturnAddressOffset(), "restore return register");
        output.emitInstruction("ldw", regFP, regSP, layout.oldFramePointerOffset(), "restore old frame pointer");
        output.emitInstruction("add", regSP, regSP, layout.frameSize(), "release frame");
        output.emitInstruction("jr", regRET, "return");

        table = globalTable;
    }

    @Override
    public void visit(AssignStatement assignStatement) {
        Register var = register;
        assignStatement.target.accept(this);
        pushReg();
        assignStatement.value.accept(this);
        output.emitInstruction("stw", register, var, 0);
        popReg();
    }

    @Override
    public void visit(BinaryExpression binaryExpression) {
        binaryExpression.leftOperand.accept(this);
        Register rop = pushReg();
        binaryExpression.rightOperand.accept(this);
        popReg();
        BinaryExpression.Operator op = binaryExpression.operator;
        if (op.isComparison())
            op = op.flipComparison();
        switch (op) {
            case ADD:
                output.emitInstruction("add", register, register, rop);
                break;
            case SUB:
                output.emitInstruction("sub", register, register, rop);
                break;
            case MUL:
                output.emitInstruction("mul", register, register, rop);
                break;
            case DIV:
                output.emitInstruction("div", register, register, rop);
                break;
            case GRT:
                output.emitInstruction("bgt", register, rop, String.format("L%d", label));
                break;
            case GRE:
                output.emitInstruction("bge", register, rop, String.format("L%d", label));
                break;
            case LST:
                output.emitInstruction("blt", register, rop, String.format("L%d", label));
                break;
            case LSE:
                output.emitInstruction("ble", register, rop, String.format("L%d", label));
                break;
            case NEQ:
                output.emitInstruction("bne", register, rop, String.format("L%d", label));
                break;
            case EQU:
                output.emitInstruction("beq", register, rop, String.format("L%d", label));
                break;
        }
    }

    @Override
    public void visit(CallStatement callStatement) {
        ProcedureEntry proc = (ProcedureEntry) table.lookup(callStatement.procedureName);
        for (int i = 0; i < callStatement.arguments.size(); i++) {
            Expression e = callStatement.arguments.get(i);
            ParameterType type = proc.parameterTypes.get(i);
            shouldLoad = !type.isReference;
            e.accept(this);
            output.emitInstruction("stw", register, regSP, proc.parameterTypes.get(i).offset, String.format("store argument #%d", i));
        }
        shouldLoad = true;
        output.emitInstruction("jal", callStatement.procedureName.toString());
    }

    @Override
    public void visit(ArrayAccess arrayAccess) {
        arrayAccess.array.accept(this);
        Register index = pushReg();
        boolean prevShouldLoad = shouldLoad;
        shouldLoad = true;
        arrayAccess.index.accept(this);
        shouldLoad = prevShouldLoad;
        pushReg();

        // push array length
        ArrayType type = (ArrayType) arrayAccess.array.dataType;
        output.emitInstruction("add", register, regNull, type.arraySize);
        output.emitInstruction("bgeu", index, register, "_indexError");
        Register offset = popReg();
        output.emitInstruction("mul", register, register, type.baseType.byteSize);

        popReg();
        output.emitInstruction("add", register, register, offset);
    }

    @Override
    public void visit(FieldAccess fieldAccess) {
        super.visit(fieldAccess);
        RecordType type = (RecordType) fieldAccess.variable.dataType;
        boolean prev = shouldLoad;

        fieldAccess.variable.accept(this);

        int offset = 0;
        for (VariableDeclaration var : type.fields) {
            if (var.name.equals(fieldAccess.field) && offset != 0)
                output.emitInstruction("add", register, register, offset);
            offset += var.typeExpression.dataType.byteSize;
        }

        shouldLoad = prev;
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        variableExpression.variable.accept(this);
        if (shouldLoad)
            output.emitInstruction("ldw", register, register, 0);
    }

    @Override
    public void visit(NamedVariable namedVariable) {
        VariableEntry entry = (VariableEntry) table.lookup(namedVariable.name);
        output.emitInstruction("add", register, regFP, entry.offset);
        if (entry.isReference)
            output.emitInstruction("ldw", register, register, 0);
    }

    @Override
    public void visit(IntLiteral intLiteral) {
        output.emitInstruction("add", register, regNull, intLiteral.value);
    }

    /**
     * Emits needed import statements, to allow usage of the predefined functions and sets the correct settings
     * for the assembler.
     */
    private void assemblerProlog() {
        output.emitImport("printi");
        output.emitImport("printc");
        output.emitImport("readi");
        output.emitImport("readc");
        output.emitImport("exit");
        output.emitImport("time");
        output.emitImport("clearAll");
        output.emitImport("setPixel");
        output.emitImport("drawLine");
        output.emitImport("drawCircle");
        output.emitImport("_indexError");
        output.emit("");
        output.emit("\t.code");
        output.emit("\t.align\t4");
    }
}
