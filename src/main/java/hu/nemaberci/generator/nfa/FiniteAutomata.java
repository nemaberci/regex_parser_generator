package hu.nemaberci.generator.nfa;

import hu.nemaberci.generator.nfa.data.AutomataState;
import hu.nemaberci.generator.regex.data.RegexAlternation;
import hu.nemaberci.generator.regex.data.RegexAtom;
import hu.nemaberci.generator.regex.data.RegexElement;
import hu.nemaberci.generator.regex.data.RegexExpression;
import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
public class FiniteAutomata {

    @Getter
    @Setter
    private AutomataState defaultState;

    @Getter
    @Setter
    private Collection<AutomataState> states = new ArrayList<>();

    private static Collection<AutomataState> fillStates(RegexAtom atom, Collection<AutomataState> states) {
        List<AutomataState> addedStates = new ArrayList<>();
        if (atom.getLiteral() != null) {
            var state = new AutomataState();
            states.add(state);
        }
        if (atom.getCharacterClass() != null) {

        }
        if (atom.getAlternation() != null) {
            return fillStates(atom.getAlternation(), states);
        }
        return addedStates;
    }

    private static Collection<AutomataState> fillStates(RegexElement element, Collection<AutomataState> states) {
        List<AutomataState> addedStates = new ArrayList<>();
        boolean isInfinite = element.getQuantifier().getMax() < Integer.MAX_VALUE;
        int repeat = isInfinite ? 1 : element.getQuantifier().getMax();
        for (int i = 0; i < repeat; i++) {
            addedStates.addAll(fillStates(element.getAtom(), states));
        }
        return addedStates;
    }

    private static Collection<AutomataState> fillStates(RegexExpression expression, Collection<AutomataState> states) {
        List<AutomataState> addedStates = new ArrayList<>();
        for (var element : expression.getElements()) {
            addedStates.addAll(fillStates(element, states));
        }
        return addedStates;
    }

    private static Collection<AutomataState> fillStates(RegexAlternation alternation, Collection<AutomataState> states) {
        List<AutomataState> addedStates = new ArrayList<>();
        for (var expression : alternation.getExpressions()) {
            addedStates.addAll(fillStates(expression, states));
        }
        return addedStates;
    }

    public static FiniteAutomata fromRegexAlternation(RegexAlternation alternation) {
        // todo
        // test: ab+
        List<AutomataState> automataStates = new ArrayList<>();
        int createdStates = fillStates(alternation, automataStates).size();
        var automata = new FiniteAutomata();
        var firstState = new AutomataState(0);
        var secondState = new AutomataState(1);
        var thirdState = new AutomataState(2, true);
        firstState.setNextStates(
            Map.of(
                'a', secondState
            )
        );
        secondState.setNextStates(
            Map.of(
                'b', thirdState
            )
        );
        thirdState.setNextStates(
            Map.of(
                'b', thirdState
            )
        );
        automata.setDefaultState(firstState);
        automata.setStates(List.of(firstState, secondState, thirdState));
        return automata;
    }

}
