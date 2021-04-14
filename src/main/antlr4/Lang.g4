grammar Lang;

@header{
}

file: use_sec? bit_width? method* statements;

use_sec: USE_SEC IDENT SEMICOLON?;

bit_width: BIT_WIDTH INTEGER_LITERAL SEMICOLON;

statement:
          block                     #block_statement
        | IDENT (OUTPUT|INPUT|TMP_INPUT|APPEND_ONLY) type IDENT (EQUAL_SIGN (phi|expression))?
          #vardecl
        | assignment #ZIGNORE
        | WHILE (LBBRACKET assignments RBBRACKET)? LPAREN expression RPAREN statement
          #while_statement
        | IF LPAREN expression RPAREN statements (ELSE statements)?
          #if_statement
        | expression                #expression_statement
        //| print_statement
        //| input_statement
        | ident LBRACKET expression RBRACKET EQUAL_SIGN expression
          #array_assignment_statement
        | RETURN expression? #return_statement;

statements: statement (SEMI statement)* SEMI?;

method:   type ident globals? LPAREN parameters? RPAREN block;
parameters: parameter (COMMA parameter)*;
parameter: type ident;

assignment: ident (COMMA ident)* EQUAL_SIGN (phi|expression|unpack);

block: LCURLY statements RCURLY;
idents: ident (COMMA ident)*;
assignments: assignment (SEMICOLON assignments)* SEMICOLON?;


expression:
  | MINUS expression
  | l=expression op=PLUS r=expression
  | l=expression op=MINUS r=expression
  | l=expression op=MULTIPLY r=expression
  | l=expression op=DIVIDE r=expression
  | l=expression op=MODULO r=expression
  | l=expression op=(LEFT_SHIFT|RIGHT_SHIFT) r=expression
  | l=expression op=(GREATER|GREATER_EQUALS|LOWER_EQUALS|LOWER) r=expression
  | l=expression op=(UNEQUALS|EQUAL_SIGN) r=expression
  | primary_expression
  ;

primary_expression:
    var_access
  | method_invocation
  | ident LBRACKET expression RBRACKET
  | LPAREN expression RPAREN
  | tuple_expression
  | array_expression;



method_invocation: ident globals? LPAREN (arguments|expression|tuple_element) RPAREN;
phi: PHI LPAREN ident COMMA ident RPAREN;
arguments: tuple_element COMMA arguments;
unpack: MULTIPLY expression;
tuple_expression: LPAREN (tuple_inner|tuple_element) RPAREN;
array_expression: LCURLY tuple_inner RCURLY;
tuple_inner: tuple_element (COMMA tuple_element)*;
tuple_element: unpack | expression;
var_access: ident;

literal: INTEGER_LITERAL;
globals: LBBRACKET globals_ RBBRACKET;
globals_: global COMMA globals_;
global: ident ARROW ident ARROW ident;
ident: IDENT|INPUT|OUTPUT;
type:
  INT #baseTypeInt
  | type LBRACKET INTEGER_LITERAL RBRACKET #array_type
  | LPAREN type (COMMA type)* RPAREN       #tuple_type
  ;


COMMENT: '//' ~([\n|\r])* [\n|\r] -> channel(HIDDEN);
WS: [ \t\f\n\r]+ -> channel(HIDDEN);

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
SEMI: ';';
INTEGER_LITERAL: [1-9] [0-9]* | '0b' [01]+ ;
//INPUT_LITERAL: '(0b([01ux]((\\{([0-9]+)\\})?))+)';
LCURLY: '{';
RCURLY: '}';
COLON: ':';
COMMA: ',';
DOT: '.';

IDENT: [A-Za-z_][A-Za-z0-9_]*;
