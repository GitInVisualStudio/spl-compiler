package de.thm.mni.compilerbau.phases._01_scanner;

import de.thm.mni.compilerbau.utils.SplError;
import de.thm.mni.compilerbau.phases._02_03_parser.Sym;
import de.thm.mni.compilerbau.absyn.Position;
import de.thm.mni.compilerbau.table.Identifier;
import de.thm.mni.compilerbau.CommandLineOptions;
import java_cup.runtime.*;
import java.util.HashMap;
import java.util.Map;

%%


%class Scanner
%public
%line
%column
%cup
%eofval{
    return new java_cup.runtime.Symbol(Sym.EOF, yyline + 1, yycolumn + 1);   //This needs to be specified when using a custom sym class name
%eofval}

%{
    public CommandLineOptions options = null;
  
    private Symbol symbol(int type) {
      return new Symbol(type, yyline + 1, yycolumn + 1);
    }

    private Symbol symbol(int type, Object value) {
      return new Symbol(type, yyline + 1, yycolumn + 1, value);
    }
%}
L       = [A-Za-z_]
H       = [\dA-Fa-f]
NL      = \n|\r
%%

\/\/[^\n]*{NL} {}
\/\*[\w\W]*\*\/ {} //TODO: i dont this works like its supposed to do
while { return symbol(Sym.WHILE);}
array { return symbol(Sym.ARRAY);}
if {return symbol(Sym.IF);}
else { return symbol(Sym.ELSE);}
proc {return symbol(Sym.PROC);}
var {return symbol(Sym.VAR);}
ref {return symbol(Sym.REF);}
type {return symbol(Sym.TYPE, yytext());}
of {return symbol(Sym.OF);}
\- {return symbol(Sym.MINUS);}
\+ {return symbol(Sym.PLUS);}
\* {return symbol(Sym.STAR);}
\/ {return symbol(Sym.SLASH);}
:= {return symbol(Sym.ASGN);}
, {return symbol(Sym.COMMA);}
: {return symbol(Sym.COLON);}
; {return symbol(Sym.SEMIC);}
= {return symbol(Sym.EQ);}
# {return symbol(Sym.NE);}
\<= {return symbol(Sym.LE);}
>= {return symbol(Sym.GE);}
\< {return symbol(Sym.LT);}
> {return symbol(Sym.GT);}
\( {return symbol(Sym.LPAREN);}
\) {return symbol(Sym.RPAREN);}
\{ {return symbol(Sym.LCURL);}
\} {return symbol(Sym.RCURL);}
\[ {return symbol(Sym.LBRACK);}
\] {return symbol(Sym.RBRACK);}
\"\\n\" {return symbol(Sym.INTLIT, 10);}
\d+ {return symbol(Sym.INTLIT, Integer.parseInt(yytext()));}
0x{H}+ {return symbol(Sym.INTLIT, Integer.parseInt(yytext().substring(2), 16));}
'.*' {
          String inputString = yytext();
          inputString = inputString.substring(1, inputString.length() - 1);
          return symbol(Sym.INTLIT, (int)inputString.charAt(0));
      }
{L}({L}|\d)* {return symbol(Sym.IDENT, yytext());}
\s {}

[^]		{throw SplError.IllegalCharacter(new Position(yyline + 1, yycolumn + 1), yytext().charAt(0));}
