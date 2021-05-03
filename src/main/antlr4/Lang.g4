grammar Lang;

@header{
}

/*
Be aware:
- C-like comments are supported
- the language is its own intermediate language (SSA is directly supported)
*/

file: SEMI*
    use_sec?   // default security lattice is the high-low lattice
    bit_width? // default bit width for integers is 32
    ((method SEMI?)|statement_w_semi)* statement_wo_semi? // a list of methods and statements
    EOF
    ;

use_sec: USE_SEC IDENT SEMI;   // "use_sec [SEC_LATTICE]", specifies the used security lattice

bit_width: BIT_WIDTH INTEGER_LITERAL SEMI;  // specifies the width of integers, default is 32

normal_statement: // a statement that ends with a semi colon
     IDENT? mod=(INPUT|TMP_INPUT) type ident ((EQUAL_SIGN|COLON_EQUAL) INPUT_LITERAL)? #inputdecl
     // declaration of the input, only allowed at global scope, the input literal specifies the bits that
     // are known and unknown of the input, but can omitted. "=" and ":=" can be used both for assignments.
     // the first IDENT is the optional security level, default is `h`
     // example: input int a;
    |  (IDENT? OUTPUT)? type ident ((EQUAL_SIGN|COLON_EQUAL) (phi|expression))? #vardecl
     // Declaration of a variable. Output variables (with optional security level, default is `l`) can only
     // be defined at global scope. Their first assignment leaks the output.
     // Variables can be assigned either a normal expression or a phi-expression (SSA)
     // example: int a = 2 * 42;
    | IDENT? INPUT? APPEND_ONLY INT ident #appenddecl
     // currently not really supported, used for implemented streams
    | assignment #IGNORE
     // an assignment is also a statement
    | RETURN (expression (COMMA expression)*)? #return_statement
     // a return statement with possibly multiple return values
     // currently only allowed at the outer most scope of a function
     // example: return 1
    | ident LBRACKET expression RBRACKET EQUAL_SIGN expression #array_assignment_statement
     // assigns a value to an array at a given index to an expression, be aware of the copy semantics of arrays
     // example: arr[i] = 10
    | expression            #expression_statement;
     // an expression might also be a statement
     // example: func(1, 2)

control_statement: // statements that do not need to be followed by a semi colon
      WHILE (LBBRACKET assignments RBBRACKET)? LPAREN expression RPAREN statement_w_semi #while_statement
     // a typical while loop
     // example: while (i != 0) { i = i + 1; }
    | IF LPAREN expression RPAREN SEMI? block SEMI? (ELSE SEMI? block)? #if_statement
     // a typical if-statement with optional else-block
     // example: if (i != 0) { x = 0 }
    | block                     #block_statement
    ;

statement_w_semi:
      control_statement SEMI?
    | normal_statement SEMI;

statement_wo_semi:
      control_statement
    | normal_statement;

statements: statement_w_semi+ statement_wo_semi | statement_wo_semi SEMI?; // a list of statements

method:  // definition of a method, with multiple return types, void return types are not supported,
         // if a function does not have a return statement (or one without an argument) then its return value
         // is undefined.
         // example: int func(int a) { return a + 1; }
         //          int int func(int a) { return a, a + 1 }
         //          (int, int) func(int a, int b) { return (a - b, a + b); }  // returning a tuple
    type+ ident globals? LPAREN parameters? RPAREN block
    ;
parameters: parameter (COMMA parameter)*; // a list of comma separated paremeters
parameter: type ident;

assignment: // assignment of a variable to a value (= and := are supported)
    ident (EQUAL_SIGN|COLON_EQUAL) (phi|expression|unpack) #single_assignment
    | (ident (COMMA ident)+ | LPAREN ident (COMMA ident)* RPAREN) (EQUAL_SIGN|COLON_EQUAL) (unpack|method_invocation) #multiple_assignment
    // Assignment of multiple variables to the result of method call that returns multiple values,
    // or to an unpack expression. The number of variables and values have to be equal
    ;

block: LCURLY SEMI* statements? SEMI* RCURLY;  // a block is enclosed in curly brackets

assignments: (assignment (SEMI assignment)* SEMI?)?;

expression:
      primary_expression
    | place_int=INTEGER_LITERAL op=DOT un=expression
      // creates a new integer with the place_int.th bit (starting at one) being the first bit of the expression
      // example: 3.1    is equivalent to    0b00100
    | op=LBRACKET place_int=INTEGER_LITERAL RBRACKET un=expression
      // same as dot notation, only with brackets
    | un=expression op=DOT select_int=INTEGER_LITERAL
      // creates an integer that is zero except for the first bit which is the select_int.th bit of the expression
      // (counting starts at one)
      // example: (1 + 2).1     is equivalent to    0b0001
    | l=expression op=LBRACKET r=expression select_int=RBRACKET
      // either accesses the select_int.th element of an array (starting at zero),
      // or the select_int.th bit (starting at one, equivalent to the dot notation)
      // depending on the type of the first expression
      // example: arr[i]
    | op=(INVERT|MINUS|TILDE) un=expression
      // an unary expression
      // -X      returns the negative integer
      // ~X      negates all bits
      // !X      negates the first bit (and zeroes all others)
    | l=expression op=(MULTIPLY|DIVIDE|MODULO) r=expression
    | l=expression op=(PLUS|MINUS) r=expression
    | l=expression op=(LEFT_SHIFT|RIGHT_SHIFT) r=expression
    | l=expression op=APPEND r=expression
      // used internally for streams
    | l=expression op=(GREATER|GREATER_EQUALS|LOWER_EQUALS|LOWER) r=expression
    | l=expression op=(UNEQUALS|EQUALS|EQUAL_SIGN) r=expression
      // = and == are equivalent
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


method_invocation: name=ident globals? LPAREN arguments RPAREN;  // example: func(1, func2(2))
phi: // phi expression used in SSA, relates to the last control statement
     // the first variable is used (in dynamic execution) if the control statements condition evaluates to true,
     // the second varibale is used if it evaluates to false           (only considers the first bit)
    PHI LPAREN ident COMMA ident RPAREN;
arguments: (tuple_element (COMMA tuple_element)*)?;

unpack:
    // an unpack expression unpacks the contents in place, can be used in conjuction with tuples or arrays
    // example: a, b = *(1, 1)
    //          a, b = *func()
    //          {1, *(2, 3)}             is equivalent to   {1, 2, 3}
    //          func(*(1, 2), 3)         is equivalent to   func(1, 2, 3)
    MULTIPLY expression;

tuple_expression: LPAREN tuple_inner RPAREN; // tuple literal, example: (1, 1)  or  (1,)
array_expression: LCURLY tuple_inner RCURLY; // array literal, example: {1, 1}  or  {1,}

tuple_inner:
    // inner part of a tuple or array literal expression, comma separated elements
    (tuple_element ((COMMA tuple_element)*| COMMA))?;
tuple_element: unpack | expression;

var_access: ident;

literal: INTEGER_LITERAL | TRUE | FALSE; // an integer literal (binary literals are supported)

globals: LBBRACKET (global (COMMA global)*)? RBBRACKET; // not used
global: ident ARROW ident ARROW ident; // not used

ident: IDENT | INPUT | OUTPUT;
type:
      VAR #baseTypeVar
      // "var": the type is infererred. Can be used in variable declariations in which the an initial expression
      // is given
    | INT #baseTypeInt
      // the basic (signed) int type
    | type LBRACKET INTEGER_LITERAL RBRACKET #array_type
      // an array type, important note: currently "X[y][z]" is an array of z y-long arrays of elements of X
    | LPAREN type (COMMA type)* RPAREN       #tuple_type
      // a tuple type
    ;


fragment BLOCK_COMMENT: '/*' .*? '*/';
fragment LINE_COMMENT: '//' ~[\r\n];
COMMENT:
      [\n]*(BLOCK_COMMENT
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

