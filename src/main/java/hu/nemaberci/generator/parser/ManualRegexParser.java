package hu.nemaberci.generator.parser;

import static hu.nemaberci.generator.parser.PCREParser.RULE_alternation;
import static hu.nemaberci.generator.parser.PCREParser.RULE_expr;

import hu.nemaberci.generator.parser.PCREParser.AlternationContext;
import hu.nemaberci.generator.parser.PCREParser.AtomContext;
import hu.nemaberci.generator.parser.PCREParser.CaptureContext;
import hu.nemaberci.generator.parser.PCREParser.Cc_atomContext;
import hu.nemaberci.generator.parser.PCREParser.Character_classContext;
import hu.nemaberci.generator.parser.PCREParser.ElementContext;
import hu.nemaberci.generator.parser.PCREParser.ExprContext;
import hu.nemaberci.generator.parser.PCREParser.LiteralContext;
import hu.nemaberci.generator.parser.PCREParser.ParseContext;
import hu.nemaberci.generator.parser.PCREParser.QuantifierContext;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;

public class ManualRegexParser {

    private void printTree(ParseTree parseTree, PCREParser parser) {
        printTree(parseTree, parser, 0);
    }

    private void printTree(ParseTree parseTree, PCREParser parser, int depth) {
        //if (parseTree instanceof TerminalNode) {
        //    var token = ((TerminalNode) parseTree).getSymbol();
        //    System.out.println(" ".repeat(depth) + parser.getVocabulary().getSymbolicName(token.getType()));
        //} else {
        if (parseTree instanceof ParserRuleContext) {
            switch (((ParserRuleContext) parseTree).getRuleIndex()) {
                case RULE_alternation: {
                    break;
                }
                case RULE_expr: {
                    break;
                }
                default: {
                    break;
                }
            }
        }
        if (parseTree instanceof AlternationContext) {
            System.out.println(" ".repeat(depth) + " ALTERNATION " + parseTree.getText());
        }
        if (parseTree instanceof ExprContext) {
            System.out.println(" ".repeat(depth) + " EXPR " + parseTree.getText());
        }
        if (parseTree instanceof ElementContext) {
            System.out.println(" ".repeat(depth) + " ELEMENT " + parseTree.getText());
        }
        if (parseTree instanceof AtomContext) {
            System.out.println(" ".repeat(depth) + " ATOM " + parseTree.getText());
        }
        if (parseTree instanceof CaptureContext) {
            System.out.println(" ".repeat(depth) + " CAPTURE " + parseTree.getText());
        }
        if (parseTree instanceof LiteralContext) {
            System.out.println(" ".repeat(depth) + " LITERAL " + parseTree.getText());
        }
        if (parseTree instanceof QuantifierContext) {
            System.out.println(" ".repeat(depth) + " QUANTIFIER " + parseTree.getText());
        }
        if (parseTree instanceof Character_classContext) {
            System.out.println(" ".repeat(depth) + " CHARACTERCLASS " + parseTree.getText());
        }
        if (parseTree instanceof Cc_atomContext) {
            System.out.println(" ".repeat(depth) + " CHARACTERCLASS ATOM " + parseTree.getText());
        }

        for (
            int i = 0; i < parseTree.getChildCount(); i++) {
            printTree(parseTree.getChild(i), parser, depth + 1);
        }

    }

    private void print(ParseContext parseContext, PCREParser parser) {
        printTree(parseContext, parser);
    }

    /**
     * Returns first regex part of the regex expression
     */
    public void parseRegex(String regexStr) {

        PCRELexer pcreLexer = new PCRELexer(CharStreams.fromString(regexStr));
        PCREParser parser = new PCREParser(new CommonTokenStream(pcreLexer));
        print(parser.parse(), parser);

    }

}
