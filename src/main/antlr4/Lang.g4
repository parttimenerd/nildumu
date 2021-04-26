grammar Lang;

@header{
}

file: SEMI* use_sec? bit_width? (statement_w_semi|(method SEMI?))* statement_wo_semi? EOF;

use_sec: USE_SEC IDENT SEMI;

bit_width: BIT_WIDTH INTEGER_LITERAL SEMI;

normal_statement:
           (IDENT? OUTPUT)? type ident (EQUAL_SIGN (phi|expression))?
          #vardecl
        | IDENT? mod=(INPUT|TMP_INPUT) type ident EQUAL_SIGN INPUT_LITERAL #inputdecl
        | IDENT? INPUT? APPEND_ONLY INT ident #appenddecl
        | assignment #IGNORE
        | RETURN expression? #return_statement
        | ident LBRACKET expression RBRACKET EQUAL_SIGN expression
          #array_assignment_statement
        | expression            #expression_statement;
        //| print_statement
        //| input_statement;

control_statement:
          WHILE (LBBRACKET assignments RBBRACKET)? LPAREN expression RPAREN statement_w_semi
          #while_statement
        | IF LPAREN expression RPAREN SEMI? block SEMI? (ELSE SEMI? block)?
          #if_statement
        | block                     #block_statement
        ;

statement_w_semi:
      normal_statement SEMI
    | control_statement SEMI?;

statement_wo_semi:
      normal_statement
    | control_statement;

statements: statement_w_semi+ statement_wo_semi | statement_wo_semi SEMI?;

method:   type ident globals? LPAREN parameters? RPAREN block;
parameters: parameter (COMMA parameter)*;
parameter: type ident;

assignment:
    ident (EQUAL_SIGN|COLON_EQUAL) (phi|expression|unpack)
  | (ident (COMMA ident)+ | LPAREN ident (COMMA ident)* RPAREN) (EQUAL_SIGN|COLON_EQUAL) unpack;

block: LCURLY SEMI* statements? SEMI* RCURLY;
//idents: ident (COMMA ident)*;
assignments: (assignment (SEMI assignment)* SEMI?)?;


expression:
    primary_expression
  | place_int=INTEGER_LITERAL op=DOT un=expression
  | op=LBRACKET place_int=INTEGER_LITERAL RBRACKET un=expression
  | un=expression op=DOT select_int=INTEGER_LITERAL
  | l=expression op=LBRACKET r=expression select_int=RBRACKET
  | op=(INVERT|MINUS|TILDE) un=expression
  | l=expression op=(MULTIPLY|DIVIDE|MODULO) r=expression
  | l=expression op=(PLUS|MINUS) r=expression
  | l=expression op=(LEFT_SHIFT|RIGHT_SHIFT) r=expression
  | l=expression op=APPEND r=expression
  | l=expression op=(GREATER|GREATER_EQUALS|LOWER_EQUALS|LOWER) r=expression
  | l=expression op=(UNEQUALS|EQUALS) r=expression
  | l=expression op=BAND r=expression
  | l=expression op=XOR r=expression
  | l=expression op=BOR r=expression
  | l=expression op=AND r=expression
  | l=expression op=OR r=expression
  ;

primary_expression:
    method_invocation
  | var_access
  | LPAREN expression RPAREN
  | tuple_expression
  | array_expression
  | literal;


method_invocation: name=ident globals? LPAREN arguments RPAREN;
phi: PHI LPAREN ident COMMA ident RPAREN;
arguments: (tuple_element (COMMA tuple_element)*)?;
unpack: MULTIPLY expression;
tuple_expression: LPAREN tuple_inner RPAREN;
array_expression: LCURLY tuple_inner RCURLY;
tuple_inner: (tuple_element ((COMMA tuple_element)*| COMMA))?;
tuple_element: unpack | expression;
var_access: ident;

literal: INTEGER_LITERAL | TRUE | FALSE;
globals: LBBRACKET (global (COMMA global)*)? RBBRACKET;
global: ident ARROW ident ARROW ident;
ident: IDENT|INPUT|OUTPUT;
type:
  VAR #baseTypeVar
  | INT #baseTypeInt
  | type LBRACKET INTEGER_LITERAL RBRACKET #array_type
  | LPAREN type (COMMA type)* RPAREN       #tuple_type
  ;


fragment BLOCK_COMMENT: '/*' .*? '*/';
fragment LINE_COMMENT: '//' ~[\r\n];
COMMENT: [\n]*(BLOCK_COMMENT
        | LINE_COMMENT) [\n]*-> channel(HIDDEN);

WS: [ \t\f\r]+ -> channel(HIDDEN);

INPUT: 'input';
TMP_INPUT: 'tmp_input';
OUTPUT: 'output';
APPEND_ONLY: 'append_only';
INT: [a]? 'int';
VAR: 'var';
RETURN: 'return';
IF: 'if';
WHILE: 'while';
ELSE: 'else';
TRUE: 'true';
FALSE: 'false';
VOID: 'void';
USE_SEC: 'use_sec';
BIT_WIDTH: 'bit_width';
PHI: 'phi';
TILDE: '~';

LOWER_EQUALS: '<=';
GREATER_EQUALS: '>=';
MODULO: '%';

PLUS: '+';
MINUS: '-';
DIVIDE: '/';
MULTIPLY: '*';
COLON_EQUAL: ':=';
EQUAL_SIGN: '=';
EQUALS: '==';
UNEQUALS: '!=';
INVERT: '!';
LOWER: '<';
GREATER: '>';
AND: '&&';
OR: '||';
BAND: '&';
BOR: '|';
XOR: '^';
LEFT_SHIFT: '<<';
RIGHT_SHIFT: '>>';
LPAREN: '(';
RPAREN: ')';
LBRACKET: '[';
LBBRACKET: '[[';
RBRACKET: ']';
RBBRACKET: ']]';
ARROW: '->';
APPEND: '@';
SEMI: (WS? ';'|WS?'\n')+;
INTEGER_LITERAL: (([1-9][0-9]*)|'0') | ('0b'([01e](('{'([0-9]+)'}')?))*);
INPUT_LITERAL: '0b' ([01ux](('{'([0-9]+)'}')?))+;
IDENT: [A-Za-z_][A-Za-z0-9_]*;

LCURLY: '{';
RCURLY: '}';
COLON: ':';
COMMA: ',';
DOT: '.';
ANY: .;

