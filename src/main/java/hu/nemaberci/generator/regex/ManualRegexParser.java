package hu.nemaberci.generator.regex;

import static hu.nemaberci.generator.parser.PCREParser.RULE_alternation;
import static hu.nemaberci.generator.parser.PCREParser.RULE_atom;
import static hu.nemaberci.generator.parser.PCREParser.RULE_capture;
import static hu.nemaberci.generator.parser.PCREParser.RULE_cc_atom;
import static hu.nemaberci.generator.parser.PCREParser.RULE_character_class;
import static hu.nemaberci.generator.parser.PCREParser.RULE_element;
import static hu.nemaberci.generator.parser.PCREParser.RULE_expr;
import static hu.nemaberci.generator.parser.PCREParser.RULE_literal;
import static hu.nemaberci.generator.parser.PCREParser.RULE_negated_character_class;
import static hu.nemaberci.generator.parser.PCREParser.RULE_quantifier;

import hu.nemaberci.generator.regex.data.RegexAlternation;
import hu.nemaberci.generator.regex.data.RegexAtom;
import hu.nemaberci.generator.regex.data.RegexCharacterclass;
import hu.nemaberci.generator.regex.data.RegexElement;
import hu.nemaberci.generator.regex.data.RegexExpression;
import hu.nemaberci.generator.regex.data.RegexLiteral;
import hu.nemaberci.generator.regex.data.RegexQuantifier;
import hu.nemaberci.generator.parser.PCRELexer;
import hu.nemaberci.generator.parser.PCREParser;
import hu.nemaberci.generator.parser.PCREParser.ParseContext;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public class ManualRegexParser {

    private static void fillAlternation(RegexAlternation alternation, ParseTree parseTree,
        PCREParser parser
    ) {
        if (parseTree instanceof ParserRuleContext
            && ((ParserRuleContext) parseTree).getRuleIndex() == RULE_expr) {
            var expression = new RegexExpression();
            for (
                int i = 0; i < parseTree.getChildCount(); i++) {
                fillExpression(expression, parseTree.getChild(i), parser);
            }
            alternation.getExpressions().add(expression);
        } else {
            for (
                int i = 0; i < parseTree.getChildCount(); i++) {
                fillAlternation(alternation, parseTree.getChild(i), parser);
            }
        }
    }

    private static void fillExpression(RegexExpression expression, ParseTree parseTree, PCREParser parser
    ) {
        if (parseTree instanceof ParserRuleContext
            && ((ParserRuleContext) parseTree).getRuleIndex() == RULE_element) {
            var element = new RegexElement();
            for (
                int i = 0; i < parseTree.getChildCount(); i++) {
                fillElement(element, parseTree.getChild(i), parser);
            }
            expression.getElements().add(element);
        } else {
            for (
                int i = 0; i < parseTree.getChildCount(); i++) {
                fillExpression(expression, parseTree.getChild(i), parser);
            }
        }
    }

    private static void fillElement(RegexElement element, ParseTree parseTree, PCREParser parser) {
        if (parseTree instanceof ParserRuleContext
            && ((ParserRuleContext) parseTree).getRuleIndex() == RULE_atom) {
            var atom = new RegexAtom();
            for (
                int i = 0; i < parseTree.getChildCount(); i++) {
                fillAtom(atom, parseTree.getChild(i), parser);
            }
            element.setAtom(atom);
        } else {
            if (parseTree instanceof ParserRuleContext
                && ((ParserRuleContext) parseTree).getRuleIndex() == RULE_quantifier) {
                var quantifier = new RegexQuantifier();
                fillQuantifier(quantifier, parseTree);
                element.setQuantifier(quantifier);
            } else {
                for (
                    int i = 0; i < parseTree.getChildCount(); i++) {
                    fillElement(element, parseTree.getChild(i), parser);
                }
            }
        }
    }

    private static void fillAtom(RegexAtom atom, ParseTree parseTree, PCREParser parser) {
        if (parseTree instanceof ParserRuleContext) {
            final var ruleIndex = ((ParserRuleContext) parseTree).getRuleIndex();
            switch (ruleIndex) {
                case RULE_alternation: {
                    setAlteration(atom, parseTree, parser);
                    break;
                }
                case RULE_character_class: {
                    setCharacterClass(false, parseTree, atom);
                    break;
                }
                case RULE_negated_character_class: {
                    setCharacterClass(true, parseTree, atom);
                    break;
                }
                case RULE_literal: {
                    setLiteral(atom, parseTree);
                    break;
                }
                case RULE_capture: {
                    for (
                        int i = 0; i < parseTree.getChildCount(); i++) {
                        fillAtom(atom, parseTree.getChild(i), parser);
                    }
                    break;
                }
                default: {
                    // Do nothing
                }
            }
        } else {
            for (
                int i = 0; i < parseTree.getChildCount(); i++) {
                fillAtom(atom, parseTree.getChild(i), parser);
            }
        }
    }

    private static void setLiteral(RegexAtom atom, ParseTree parseTree) {
        var literal = new RegexLiteral();
        for (
            int i = 0; i < parseTree.getChildCount(); i++) {
            fillLiteral(literal, parseTree.getChild(i));
        }
        atom.setLiteral(literal);
    }

    private static void setCharacterClass(boolean negated, ParseTree parseTree, RegexAtom atom) {
        var characterClass = new RegexCharacterclass();
        characterClass.setNegated(negated);
        for (
            int i = 0; i < parseTree.getChildCount(); i++) {
            fillCharacterClass(characterClass, parseTree.getChild(i));
        }
        atom.setCharacterClass(characterClass);
    }

    private static void setAlteration(RegexAtom atom, ParseTree parseTree, PCREParser parser) {
        var alternation = new RegexAlternation();
        for (
            int i = 0; i < parseTree.getChildCount(); i++) {
            fillAlternation(alternation, parseTree.getChild(i), parser);
        }
        atom.setAlternation(alternation);
    }

    private static void fillCharacterClass(RegexCharacterclass characterClass, ParseTree parseTree) {
        if (parseTree instanceof ParserRuleContext
            && ((ParserRuleContext) parseTree).getRuleIndex() == RULE_cc_atom) {
            createCharacterClassContent(characterClass, parseTree);
        } else {
            if (!(parseTree instanceof TerminalNode)) {
                if (parseTree.getText().equals("]")) {
                    characterClass.getAcceptedLiterals().add(new RegexLiteral(']'));
                }
                if (parseTree.getText().equals("-")) {
                    characterClass.getAcceptedLiterals().add(new RegexLiteral('-'));
                }
            }
        }
    }

    private static void createCharacterClassContent(RegexCharacterclass characterClass,
        ParseTree parseTree
    ) {
        int length = parseTree.getText().length();
        if (length == 1) {
            char content = parseTree.getText().charAt(0);
            characterClass.getAcceptedLiterals().add(new RegexLiteral(content));
        } else if (length == 2) {
            char content = parseTree.getText().charAt(1);
            characterClass.getAcceptedLiterals().add(new RegexLiteral(content));
        } else if (length == 3) {
            char startChar = parseTree.getText().charAt(0);
            char endChar = parseTree.getText().charAt(2);
            for (int i = startChar; i <= endChar; i++) {
                characterClass.getAcceptedLiterals().add(new RegexLiteral((char) i));
            }
        }
    }

    private static void fillLiteral(RegexLiteral literal, ParseTree parseTree) {
        literal.setCharacter(parseTree.getText().charAt(0));
    }

    private static void fillQuantifier(RegexQuantifier quantifier, ParseTree parseTree) {
        switch (parseTree.getText().charAt(0)) {
            case '?': {
                quantifier.setMin(0);
                quantifier.setMax(1);
                break;
            }
            case '*': {
                quantifier.setMin(0);
                quantifier.setMax(Integer.MAX_VALUE);
                break;
            }
            case '+': {
                quantifier.setMin(1);
                quantifier.setMax(Integer.MAX_VALUE);
                break;
            }
            default: {
                var parts = parseTree.getText()
                    .substring(1, parseTree.getText().length() - 1)
                    .split(",");
                if (parts.length > 1) {
                    if (parts[0].length() > 0) {
                        quantifier.setMin(Integer.parseInt(parts[0]));
                    } else {
                        quantifier.setMin(0);
                    }
                    if (parts[1].length() > 0) {
                        quantifier.setMax(Integer.parseInt(parts[1]));
                    } else {
                        quantifier.setMax(Integer.MAX_VALUE);
                    }
                } else {
                    quantifier.setMax(Integer.parseInt(parts[0]));
                    quantifier.setMin(Integer.parseInt(parts[0]));
                }

            }
        }
    }

    private static RegexAlternation parse(ParseContext parseContext, PCREParser parser) {
        var alternation = new RegexAlternation();
        fillAlternation(alternation, parseContext, parser);
        return alternation;
    }

    /**
     * Returns first regex part of the regex expression
     */
    public static RegexAlternation parseRegex(String regexStr) {

        PCRELexer pcreLexer = new PCRELexer(CharStreams.fromString(regexStr));
        PCREParser parser = new PCREParser(new CommonTokenStream(pcreLexer));
        return parse(parser.parse(), parser);

    }

}
