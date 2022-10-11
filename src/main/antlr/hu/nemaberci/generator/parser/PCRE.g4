/*
 * Copyright (c) 2014-2022 by Bart Kiers
 *
 * The MIT license.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * Project      : PCRE Parser, an ANTLR 4 grammar for PCRE
 * Developed by : Bart Kiers, bart@big-o.nl
 * Also see     : https://github.com/bkiers/pcre-parser
 */
grammar PCRE;

@header {
package hu.nemaberci.generator.parser;
}

// Most single line comments above the lexer- and  parser rules 
// are copied from the official PCRE man pages (last updated: 
// 10 January 2012): http://www.pcre.org/pcre.txt
parse
 : alternation EOF
 ;

// ALTERNATION
//
//         expr|expr|expr...
alternation
 : expr ('|' expr)*
 ;

expr
 : element*
 ;

element
 : atom quantifier?
 ;

// QUANTIFIERS
//
//         ?           0 or 1
//         *           0 or more
//         +           1 or more
//         {n}         exactly n
//         {n,m}       at least n, no more than m
//         {n,}        n or more
//         {,n}        n or less
quantifier
 : '?'
 | '+'
 | '*'
 | '{' number '}'
 | '{' min ',' '}'
 | '{' ',' max '}'
 | '{' min ',' max '}'
 ;

quantifier_type
 : '+'
 | '?'
 | /* nothing */
 ;

// CHARACTER CLASSES
//
//          [...]                       positive character class
//          [-...]                      character class can contain '-' character at the start or in ranges
//          []...]                      character classes can contain the ']' character only as the first character
//          []-...]                     if a character class contains both ']' and '-', it must appear in this order
//          [^...]                      negative character class
//          [^-...]                     character class can contain '-' character at the start or in ranges
//          [^]...]                     character classes can contain the ']' character only as the first character
//          [^]-...]                    if a character class contains both ']' and '-', it must appear in this order
//
//
negated_character_class
 : '[' Caret CharacterClassEnd Hyphen cc_atom* ']'
 | '[' Caret CharacterClassEnd cc_atom* ']'
 | '[' Caret Hyphen cc_atom* ']'
 | '[' Caret cc_atom+ ']'
 ;

character_class
 : '[' CharacterClassEnd Hyphen cc_atom* ']'
 | '[' CharacterClassEnd cc_atom* ']'
 | '[' Hyphen cc_atom* ']'
 | '[' cc_atom+ ']'
 ;

// CAPTURING
//
//         (...)           capturing group
//         (?<name>...)    named capturing group (Perl)
//         (?'name'...)    named capturing group (Perl)
//         (?P<name>...)   named capturing group (Python)
//         (?:...)         non-capturing group
//         (?|...)         non-capturing group; reset group numbers for
//                          capturing groups in each alternative
//
// ATOMIC GROUPS
//
//         (?>...)         atomic, non-capturing group
capture
 : '(' alternation ')'
 ;

atom
 : shared_atom
 | literal
 | negated_character_class
 | character_class
 | capture
 | Dot
 | Caret
 | OneDataUnit
 | ExtendedUnicodeChar
 ;

cc_atom
 : cc_range
 | shared_atom
 | cc_literal
 ;

cc_range
 : cc_literal Hyphen cc_literal;

shared_atom
 : ControlChar
 | DecimalDigit
 | NotDecimalDigit
 | HorizontalWhiteSpace
 | NotHorizontalWhiteSpace
 | NotNewLine
 | CharWithProperty
 | CharWithoutProperty
 | NewLineSequence
 | WhiteSpace
 | NotWhiteSpace
 | VerticalWhiteSpace
 | NotVerticalWhiteSpace
 | WordChar
 | NotWordChar
 | Backslash . // will match "unfinished" escape sequences, like `\x`
 ;

literal
 : shared_literal
 | CharacterClassEnd
 ;

cc_literal
 : shared_literal
 | Dot
 | CharacterClassStart
 | Caret
 | QuestionMark
 | Plus
 | Star
 | Pipe
 | OpenParen
 | CloseParen
 ;

shared_literal
 : octal_char
 | letter
 | digit
 | BellChar
 | EscapeChar
 | FormFeed
 | NewLine
 | CarriageReturn
 | Tab
 | HexChar
 | Quoted
 | OpenBrace
 | CloseBrace
 | Comma
 | Hyphen
 | LessThan
 | GreaterThan
 | SingleQuote
 | Underscore
 | Colon
 | Hash
 | Equals
 | Exclamation
 | Ampersand
 | OtherChar
 ;

number
 : digits
 ;

min
 : digits
 ;

max
 : digits
 ;

octal_char
 : ( Backslash (D0 | D1 | D2 | D3) octal_digit octal_digit
   | Backslash octal_digit octal_digit                     
   )

 ;

octal_digit
 : D0 | D1 | D2 | D3 | D4 | D5 | D6 | D7
 ;
 
digits
 : digit+
 ;

digit
 : D0 | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9
 ;

name
 : alpha_nums
 ;

alpha_nums
 : (letter | Underscore) (letter | Underscore | digit)*
 ;

non_close_parens
 : non_close_paren+
 ;

non_close_paren
 : ~CloseParen
 ;

letter
 : ALC | BLC | CLC | DLC | ELC | FLC | GLC | HLC | ILC | JLC | KLC | LLC | MLC | NLC | OLC | PLC | QLC | RLC | SLC | TLC | ULC | VLC | WLC | XLC | YLC | ZLC |
   AUC | BUC | CUC | DUC | EUC | FUC | GUC | HUC | IUC | JUC | KUC | LUC | MUC | NUC | OUC | PUC | QUC | RUC | SUC | TUC | UUC | VUC | WUC | XUC | YUC | ZUC
 ;

// QUOTING
//
//         \x         where x is non-alphanumeric is a literal x
//         \Q...\E    treat enclosed characters as literal
Quoted      : '\\' NonAlphaNumeric;

// CHARACTERS
//
//         \a         alarm, that is, the BEL character (hex 07)
//         \cx        "control-x", where x is any ASCII character
//         \e         escape (hex 1B)
//         \f         form feed (hex 0C)
//         \n         newline (hex 0A)
//         \r         carriage return (hex 0D)
//         \t         tab (hex 09)
//         \ddd       character with octal code ddd, or backreference
//         \xhh       character with hex code hh
//         \x{hhh..}  character with hex code hhh..
BellChar       : '\\a';
ControlChar    : '\\c' ASCII?;
EscapeChar     : '\\e';
FormFeed       : '\\f';
NewLine        : '\\n';
CarriageReturn : '\\r';
Tab            : '\\t';
Backslash      : '\\';
HexChar        : '\\x' ( HexDigit HexDigit
                       | '{' HexDigit HexDigit HexDigit+ '}'
                       )
               ;

// CHARACTER TYPES
//
//         .          any character except newline;
//                      in dotall mode, any character whatsoever
//         \C         one data unit, even in UTF mode (best avoided)
//         \d         a decimal digit
//         \D         a character that is not a decimal digit
//         \h         a horizontal white space character
//         \H         a character that is not a horizontal white space character
//         \N         a character that is not a newline
//         \p{xx}     a character with the xx property
//         \P{xx}     a character without the xx property
//         \R         a newline sequence
//         \s         a white space character
//         \S         a character that is not a white space character
//         \v         a vertical white space character
//         \V         a character that is not a vertical white space character
//         \w         a "word" character
//         \W         a "non-word" character
//         \X         an extended Unicode sequence
//
//       In  PCRE,  by  default, \d, \D, \s, \S, \w, and \W recognize only ASCII
//       characters, even in a UTF mode. However, this can be changed by setting
//       the PCRE_UCP option.
Dot                     : '.';
OneDataUnit             : '\\C';
DecimalDigit            : '\\d';
NotDecimalDigit         : '\\D';
HorizontalWhiteSpace    : '\\h';
NotHorizontalWhiteSpace : '\\H';
NotNewLine              : '\\N';
CharWithProperty        : '\\p{' UnderscoreAlphaNumerics '}';
CharWithoutProperty     : '\\P{' UnderscoreAlphaNumerics '}';
NewLineSequence         : '\\R';
WhiteSpace              : '\\s';
NotWhiteSpace           : '\\S';
VerticalWhiteSpace      : '\\v';
NotVerticalWhiteSpace   : '\\V';
WordChar                : '\\w';
NotWordChar             : '\\W';
ExtendedUnicodeChar     : '\\X';

// CHARACTER CLASSES
//
//         [...]       positive character class
//         [^...]      negative character class
//         [x-y]       range (can be used for hex characters)
//         [[:xxx:]]   positive POSIX named set
//         [[:^xxx:]]  negative POSIX named set
//
//         alnum       alphanumeric
//         alpha       alphabetic
//         ascii       0-127
//         blank       space or tab
//         cntrl       control character
//         digit       decimal digit
//         graph       printing, excluding space
//         lower       lower case letter
//         print       printing, including space
//         punct       printing, excluding alphanumeric
//         space       white space
//         upper       upper case letter
//         word        same as \w
//         xdigit      hexadecimal digit
//
//       In PCRE, POSIX character set names recognize only ASCII  characters  by
//       default,  but  some  of them use Unicode properties if PCRE_UCP is set.
//       You can use \Q...\E inside a character class.
CharacterClassStart  : '[';
CharacterClassEnd    : ']';
Caret                : '^';
Hyphen               : '-';

QuestionMark : '?';
Plus         : '+';
Star         : '*';
OpenBrace    : '{';
CloseBrace   : '}';
Comma        : ',';

Pipe        : '|';
OpenParen   : '(';
CloseParen  : ')';
LessThan    : '<';
GreaterThan : '>';
SingleQuote : '\'';
Underscore  : '_';
Colon       : ':';
Hash        : '#';
Equals      : '=';
Exclamation : '!';
Ampersand   : '&';

ALC : 'a';
BLC : 'b';
CLC : 'c';
DLC : 'd';
ELC : 'e';
FLC : 'f';
GLC : 'g';
HLC : 'h';
ILC : 'i';
JLC : 'j';
KLC : 'k';
LLC : 'l';
MLC : 'm';
NLC : 'n';
OLC : 'o';
PLC : 'p';
QLC : 'q';
RLC : 'r';
SLC : 's';
TLC : 't';
ULC : 'u';
VLC : 'v';
WLC : 'w';
XLC : 'x';
YLC : 'y';
ZLC : 'z';

AUC : 'A';
BUC : 'B';
CUC : 'C';
DUC : 'D';
EUC : 'E';
FUC : 'F';
GUC : 'G';
HUC : 'H';
IUC : 'I';
JUC : 'J';
KUC : 'K';
LUC : 'L';
MUC : 'M';
NUC : 'N';
OUC : 'O';
PUC : 'P';
QUC : 'Q';
RUC : 'R';
SUC : 'S';
TUC : 'T';
UUC : 'U';
VUC : 'V';
WUC : 'W';
XUC : 'X';
YUC : 'Y';
ZUC : 'Z';

D1 : '1';
D2 : '2';
D3 : '3';
D4 : '4';
D5 : '5';
D6 : '6';
D7 : '7';
D8 : '8';
D9 : '9';
D0 : '0';

OtherChar : . ;

// fragments
fragment UnderscoreAlphaNumerics : ('_' | AlphaNumeric)+;
fragment AlphaNumerics           : AlphaNumeric+;
fragment AlphaNumeric            : [a-zA-Z0-9];
fragment NonAlphaNumeric         : ~[a-zA-Z0-9];
fragment HexDigit                : [0-9a-fA-F];
fragment ASCII                   : [\u0000-\u007F];
