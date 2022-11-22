package hu.nemaberci.generator.regex.parser;

import hu.nemaberci.generator.regex.data.RegexFlag;
import hu.nemaberci.generator.regex.data.RegexNode;
import hu.nemaberci.generator.regex.data.RegexNode.RegexNodeType;
import hu.nemaberci.generator.regex.data.RegexParseResult;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
public class RegexParser {

    private RegexParser() {}

    public static RegexParseResult parseRegex(String regex) {
        List<RegexFlag> flags = new ArrayList<>();
        String regexToParse = regex;
        boolean hasUnparsedFlags = true;
        if (regexToParse.startsWith("^")) {
            regexToParse = regexToParse.substring(1);
            flags.add(RegexFlag.START_OF_STRING);
        }
        while (hasUnparsedFlags) {
            switch (regexToParse.substring(regexToParse.length() - 2)) {
                case "\\i": {
                    regexToParse = regexToParse.substring(0, regexToParse.length() - 2);
                    flags.add(RegexFlag.CASE_INDEPENDENT_ASCII);
                    break;
                }
                default: {
                    hasUnparsedFlags = false;
                }
            }
        }
        if (regexToParse.endsWith("$")) {
            regexToParse = regexToParse.substring(0, regexToParse.length() - 1);
            flags.add(RegexFlag.END_OF_STRING);
        }
        final var parseResult = new RegexParseResult()
            .setFirstNode(parseRegex(regexToParse, true));
        parseResult.getFlags().addAll(flags);
        return parseResult;
    }

    private static RegexNode parseRegex(String expression, boolean newExpression) {

        RegexNode createdNode = new RegexNode();

        final var expressionLength = expression.length();
        final var nodeParts = new ArrayList<RegexNode>();
        if (newExpression) {
            int stack = 0;
            int last = 0;
            for (int i = 0; i <= expression.length(); i++) {
                if (i == expressionLength || (expression.charAt(i) == '|' && stack == 0)) {
                    if (last == 0 && i == expressionLength) {
                        return parseRegex(
                            expression, false);
                    }
                    var subExpression = parseRegex(
                        expression.substring(last, i),
                        true
                    );
                    nodeParts.add(subExpression);
                    last = i + 1;
                } else if (expression.charAt(i) == '(' && ((i > 0
                    && expression.charAt(i - 1) != '\\') || i == 0)) {
                    stack++;
                } else if (expression.charAt(i) == ')' && ((i > 0
                    && expression.charAt(i - 1) != '\\') || i == 0)) {
                    stack--;
                }
            }
            if (nodeParts.size() == 1) {
                return nodeParts.get(0);
            }
            createdNode.getParts().addAll(nodeParts);
            createdNode.setType(RegexNodeType.ALTERNATION);
        } else {
            int i = 0;
            while (i < expressionLength) {
                switch (expression.charAt(i)) {
                    case '(': {
                        i = handleBracket(expression, nodeParts, i);
                        break;
                    }
                    case '[': {
                        i = handleCharacterRange(expression, expressionLength, nodeParts, i);
                        break;
                    }
                    case '{': {
                        i = handleQuantifier(expression, nodeParts, i);
                        break;
                    }
                    case '*': {
                        handleStar(nodeParts);
                        break;
                    }
                    case '+': {
                        handleOneOrMore(nodeParts);
                        break;
                    }
                    case '?': {
                        handleOptional(nodeParts);
                        break;
                    }
                    case '\\': {
                        i++;
                        nodeParts.add(getCharacterNode(expression, i));
                        break;
                    }
                    case '.': {
                        var newNode = new RegexNode()
                            .setType(RegexNodeType.ANY);
                        nodeParts.add(newNode);
                        break;
                    }
                    default: {
                        nodeParts.add(getCharacterNode(expression, i));
                    }
                }
                i++;
            }
            if (nodeParts.size() == 1) {
                return nodeParts.get(0);
            }
            createdNode.setType(RegexNodeType.CONCATENATION);
            createdNode.getParts().addAll(nodeParts);
        }

        return createdNode;
    }

    /*
     * {n}     ==> (a{n})     == (a)(a)(a)...(a) {n times}
     * {, n}   ==> (a{, n})   == (a?)(a?)(a?)...(a?) {n times}
     * {n, }   ==> (a{n, })   == (a)(a)(a)...(a) {n times} (a*)
     * {n, m}  ==> (a{n, m})  == (a)(a)(a)...(a) {n times} (a?)(a?)(a?) {m - n times}
     * */
    private static int handleQuantifier(String expression, ArrayList<RegexNode> nodeParts, int i) {
        int bracketClosedAt = 0;
        for (int j = i + 2; j < expression.length(); j++) {
            if (expression.charAt(j) == '}') {
                bracketClosedAt = j;
                break;
            }
        }
        if (bracketClosedAt == 0) {
            throw new IllegalArgumentException("Curly bracket not closed in '" + expression + "'");
        }
        var contents = expression.substring(i + 1, bracketClosedAt);
        int min = 0;
        int max = -1;
        if (contents.contains(",")) {
            var curlyBracketParts = contents.split(",");
            if (curlyBracketParts.length > 2) {
                throw new IllegalArgumentException(
                    "Curly brackets contains more than 3 parts '" + expression
                        + "'");
            }
            if (curlyBracketParts[0] != null && !curlyBracketParts[0].trim().equals("")) {
                min = Integer.parseUnsignedInt(curlyBracketParts[0].trim());
            }
            if (
                curlyBracketParts.length > 1 &&
                curlyBracketParts[1] != null &&
                !curlyBracketParts[1].trim().equals("")
            ) {
                max = Integer.parseUnsignedInt(curlyBracketParts[1].trim());
            }
        } else {
            min = Integer.parseUnsignedInt(contents);
            max = Integer.parseUnsignedInt(contents);
        }
        final var containedNode = nodeParts.get(nodeParts.size() - 1);
        final var newNode = new RegexNode()
            .setType(RegexNodeType.CONCATENATION);
        for (int j = 0; j < min; j++) {
            newNode.getParts().add(containedNode.copy());
        }
        if (max != -1) {
            for (int j = 0; j < max - min; j++) {
                newNode.getParts().add(containedNode.copy());
                handleOptional(newNode.getParts());
            }
        } else {
            newNode.getParts().add(containedNode.copy());
            handleStar(newNode.getParts());
        }
        nodeParts.set(nodeParts.size() - 1, newNode);
        i = bracketClosedAt;
        return i;
    }

    private static int handleBracket(String expression, List<RegexNode> nodeParts,
        int i
    ) {
        int last = ++i;
        int stack = 1;
        while (i < expression.length() && stack != 0) {
            if (expression.charAt(i) == '(' && (i > 0 && expression.charAt(i - 1) != '\\')) {
                stack++;
            }
            if (expression.charAt(i) == ')' && (i > 0 && expression.charAt(i - 1) != '\\')) {
                stack--;
            }
            i++;
        }
        if (stack != 0) {
            throw new IllegalArgumentException(
                "Missing right bracket in " + expression);
        }
        i--;
        var subExpression = parseRegex(
            expression.substring(last, i), true);
        nodeParts.add(subExpression);
        return i;
    }

    private static int handleCharacterRange(String expression, int expressionLength,
        List<RegexNode> nodeParts,
        int i
    ) {
        int bracketClosedAt = 0;
        var nodeStart = i;
        final boolean isNegated = expression.charAt(i + 1) == '^';
        for (int j = isNegated ? i + 3 : i + 2; j < expressionLength; j++) {
            if (expression.charAt(j) == ']') {
                bracketClosedAt = j;
                break;
            }
        }
        if (bracketClosedAt == 0) {
            throw new IllegalArgumentException("Square bracket not closed in '" + expression + "'");
        }
        final var newNode = new RegexNode();
        List<Character> charList = new ArrayList<>();
        int j;
        if (isNegated) {
            newNode.setType(RegexNodeType.NEGATED_CHARACTER_RANGE);
            j = i + 2;
        } else {
            newNode.setType(RegexNodeType.CHARACTER_RANGE);
            j = i + 1;
        }
        while (j < bracketClosedAt) {
            if (j + 2 < bracketClosedAt && expression.charAt(j + 1) == '-') {
                char from = expression.charAt(j);
                char to = expression.charAt(j + 2);
                for (char c = from; c <= to; c++) {
                    charList.add(c);
                }
                j += 2;
            } else {
                charList.add(expression.charAt(j));
            }
            j++;
        }
        charList.sort(Character::compareTo);
        newNode.setCharacters(
            ArrayUtils.toPrimitive(charList.toArray(new Character[0]))
        );
        nodeParts.add(newNode);
        i = bracketClosedAt;
        return i;
    }

    private static void handleStar(List<RegexNode> nodeParts) {
        if (nodeParts.isEmpty()) {
            throw new IllegalArgumentException("Illegal '*' character ");
        }
        final var containedNode = nodeParts.get(nodeParts.size() - 1);
        var virtualNode = getVirtualNode(containedNode, RegexNodeType.STAR);
        nodeParts.set(nodeParts.size() - 1, virtualNode);
    }

    private static void handleOneOrMore(List<RegexNode> nodeParts) {
        if (nodeParts.isEmpty()) {
            throw new IllegalArgumentException("Illegal '+' character ");
        }
        final var containedNode = nodeParts.get(nodeParts.size() - 1);
        final var virtualNode = getVirtualNode(containedNode, RegexNodeType.STAR);
        final var newNode = getNewNode(RegexNodeType.CONCATENATION);
        newNode
            .getParts().addAll(List.of(containedNode, virtualNode));
        nodeParts.set(nodeParts.size() - 1, newNode);
    }

    private static void handleOptional(List<RegexNode> nodeParts) {
        if (nodeParts.isEmpty()) {
            throw new IllegalArgumentException("Illegal '?' character ");
        }
        final var containedNode = nodeParts.get(nodeParts.size() - 1);
        final var virtualNode = getVirtualNode(containedNode, RegexNodeType.EMPTY);
        final var newNode = getNewNode(RegexNodeType.ALTERNATION);
        newNode
            .getParts().addAll(List.of(containedNode, virtualNode));
        nodeParts.set(nodeParts.size() - 1, newNode);
    }

    private static RegexNode getNewNode(RegexNodeType type) {
        return new RegexNode()
            .setType(type);
    }

    private static RegexNode getVirtualNode(RegexNode containedNode, RegexNodeType type) {
        return getNewNode(type)
            .setExpression(containedNode);
    }

    private static RegexNode getCharacterNode(String expression, int i) {
        return new RegexNode()
            .setType(RegexNodeType.CHARACTER)
            .setCharacters(new char[]{expression.charAt(i)});
    }

}
