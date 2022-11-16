package de.thm.mni.compilerbau.phases._05_varalloc;

import de.thm.mni.compilerbau.CommandLineOptions;
import de.thm.mni.compilerbau.absyn.*;
import de.thm.mni.compilerbau.absyn.visitor.DoNothingVisitor;
import de.thm.mni.compilerbau.table.*;
import de.thm.mni.compilerbau.utils.*;

import javax.swing.text.html.Option;
import java.net.Proxy;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * This class is used to calculate the memory needed for variables and stack frames of the currently compiled SPL program.
 * Those value have to be stored in their corresponding fields in the {@link ProcedureEntry}, {@link VariableEntry} and
 * {@link ParameterType} classes.
 */
public class VarAllocator extends DoNothingVisitor {
    public static final int REFERENCE_BYTESIZE = 4;

    private final CommandLineOptions options;
    private int varOffset, maxCalleSize;
    private SymbolTable table;

    /**
     * @param options The options passed to the compiler
     */
    public VarAllocator(CommandLineOptions options) {
        this.options = options;
    }

    public void allocVars(Program program, SymbolTable table) {
        this.table = table;
        program.accept(this);
        if (options.phaseOption == options.phaseOption.VARS)
            formatVars(program, table);
    }

    @Override
    public void visit(Program program) {
        program.declarations.forEach(x -> x.accept(this));
    }

    @Override
    public void visit(ProcedureDeclaration procedureDeclaration) {
        ProcedureEntry proc = (ProcedureEntry) table.lookup(procedureDeclaration.name);

        StackLayout layout = proc.stackLayout;
        SymbolTable global = table;
        table = proc.localTable;

        // also set the offset of the parameter entry
        varOffset = 0;
        proc.parameterTypes.forEach(x -> {
            x.offset = varOffset;
            varOffset += x.isReference ? REFERENCE_BYTESIZE : x.type.byteSize;
        });
        layout.argumentAreaSize = varOffset;

        varOffset = 0;
        procedureDeclaration.parameters.forEach(x -> x.accept(this));

        varOffset = 0;
        procedureDeclaration.variables.forEach(x -> x.accept(this));
        layout.localVarAreaSize = varOffset;

        maxCalleSize = 0;
        procedureDeclaration.body.forEach(x -> x.accept(this));

        layout.outgoingAreaSize = maxCalleSize;
        table = global;
    }

    @Override
    public void visit(WhileStatement whileStatement) {
        whileStatement.body.accept(this);
    }

    @Override
    public void visit(IfStatement ifStatement) {
        ifStatement.thenPart.accept(this);
        ifStatement.elsePart.accept(this);
    }

    @Override
    public void visit(CompoundStatement compoundStatement) {
        compoundStatement.statements.forEach(x -> x.accept(this));
    }

    @Override
    public void visit(CallStatement callStatement) {
        ProcedureEntry entry = (ProcedureEntry)table.lookup(callStatement.procedureName);
        Integer argumentSize = entry.parameterTypes.stream().map(x -> x.isReference ? REFERENCE_BYTESIZE : x.type.byteSize).reduce(0, Integer::sum);
        if (argumentSize > this.maxCalleSize)
            this.maxCalleSize = argumentSize;
    }

    @Override
    public void visit(ParameterDeclaration parameterDeclaration) {
        VariableEntry entry = (VariableEntry) table.lookup(parameterDeclaration.name);
        entry.offset = varOffset;
        varOffset += entry.isReference ? REFERENCE_BYTESIZE : entry.type.byteSize;
    }

    @Override
    public void visit(VariableDeclaration variableDeclaration) {
        VariableEntry entry = (VariableEntry) table.lookup(variableDeclaration.name);
        varOffset += entry.type.byteSize;
        entry.offset = -varOffset;
    }

    /**
     * Formats and prints the variable allocation to a human-readable format
     * The stack layout
     *
     * @param program The abstract syntax tree of the program
     * @param table   The symbol table containing all symbols of the spl program
     */
    private static void formatVars(Program program, SymbolTable table) {
        program.declarations.stream().filter(dec -> dec instanceof ProcedureDeclaration).map(dec -> (ProcedureDeclaration) dec).forEach(procDec -> {
            ProcedureEntry entry = (ProcedureEntry) table.lookup(procDec.name);

            var isLeafOptimized = entry.stackLayout.isOptimizedLeafProcedure;
            var varparBasis = (isLeafOptimized ? "SP" : "FP");

            AsciiGraphicalTableBuilder ascii = new AsciiGraphicalTableBuilder();
            ascii.line("...", AsciiGraphicalTableBuilder.Alignment.CENTER);

            {
                final var zipped = IntStream.range(0, procDec.parameters.size()).boxed()
                        .map(i -> new Pair<>(procDec.parameters.get(i), new Pair<>(((VariableEntry) entry.localTable.lookup(procDec.parameters.get(i).name)), entry.parameterTypes.get(i))))
                        .sorted(Comparator.comparing(p -> Optional.ofNullable(p.second.first.offset).map(o -> -o).orElse(Integer.MIN_VALUE)));

                zipped.forEach(v -> {
                    boolean consistent = Objects.equals(v.second.first.offset, v.second.second.offset);

                    ascii.line("par " + v.first.name.toString(), "<- " + varparBasis + " + " +
                                    (consistent ?
                                            StringOps.toString(v.second.first.offset) :
                                            String.format("INCONSISTENT(%s/%s)",
                                                    StringOps.toString(v.second.first.offset),
                                                    StringOps.toString(v.second.second.offset))),
                            AsciiGraphicalTableBuilder.Alignment.LEFT);
                });
            }

            ascii.sep("BEGIN", "<- " + varparBasis);
            if (!procDec.variables.isEmpty()) {
                procDec.variables.stream()
                        .map(v -> new AbstractMap.SimpleImmutableEntry<>(v, ((VariableEntry) entry.localTable.lookup(v.name))))
                        .sorted(Comparator.comparing(e -> Try.execute(() -> -e.getValue().offset).getOrElse(0)))
                        .forEach(v -> ascii.line("var " + v.getKey().name.toString(),
                                "<- " + varparBasis + " - " + Optional.ofNullable(v.getValue().offset).map(o -> -o).map(StringOps::toString).orElse("NULL"),
                                AsciiGraphicalTableBuilder.Alignment.LEFT));

                if (!isLeafOptimized) ascii.sep("");
            }

            if (isLeafOptimized) ascii.close("END");
            else {
                ascii.line("Old FP",
                        "<- SP + " + Try.execute(entry.stackLayout::oldFramePointerOffset).map(Objects::toString).getOrElse("UNKNOWN"),
                        AsciiGraphicalTableBuilder.Alignment.LEFT);

                ascii.line("Old Return",
                        "<- FP - " + Try.execute(() -> -entry.stackLayout.oldReturnAddressOffset()).map(Objects::toString).getOrElse("UNKNOWN"),
                        AsciiGraphicalTableBuilder.Alignment.LEFT);

                if (entry.stackLayout.outgoingAreaSize == null || entry.stackLayout.outgoingAreaSize > 0) {

                    ascii.sep("outgoing area");

                    if (entry.stackLayout.outgoingAreaSize != null) {
                        var max_args = entry.stackLayout.outgoingAreaSize / 4;

                        for (int i = 0; i < max_args; ++i) {
                            ascii.line(String.format("arg %d", max_args - i),
                                    String.format("<- SP + %d", (max_args - i - 1) * 4),
                                    AsciiGraphicalTableBuilder.Alignment.LEFT);
                        }
                    } else {
                        ascii.line("UNKNOWN SIZE", AsciiGraphicalTableBuilder.Alignment.LEFT);
                    }
                }

                ascii.sep("END", "<- SP");
                ascii.line("...", AsciiGraphicalTableBuilder.Alignment.CENTER);
            }

            System.out.printf("Variable allocation for procedure '%s':\n", procDec.name);
            System.out.printf("  - size of argument area = %s\n", StringOps.toString(entry.stackLayout.argumentAreaSize));
            System.out.printf("  - size of localvar area = %s\n", StringOps.toString(entry.stackLayout.localVarAreaSize));
            System.out.printf("  - size of outgoing area = %s\n", StringOps.toString(entry.stackLayout.outgoingAreaSize));
            System.out.printf("  - frame size = %s\n", Try.execute(entry.stackLayout::frameSize).map(Objects::toString).getOrElse("UNKNOWN"));
            System.out.println();
            if (isLeafOptimized) System.out.println("  Stack layout (leaf optimized):");
            else System.out.println("  Stack layout:");
            System.out.println(StringOps.indent(ascii.toString(), 4));
            System.out.println();
        });
    }
}
