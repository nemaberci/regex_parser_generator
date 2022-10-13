package hu.nemaberci.generator.nfa.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class NonDeterministicAutomataState {

    private static int currId = 0;
    int id;
    Map<Character, NonDeterministicAutomataState> transitions = new HashMap<>();
    List<NonDeterministicAutomataState> epsilonTransitions = new ArrayList<>();
    boolean terminating;
    public NonDeterministicAutomataState() {
        this(currId++);
    }
    public NonDeterministicAutomataState(int id) {
        this(id, false);
    }
    public NonDeterministicAutomataState(int id, boolean terminating) {
        this.id = id;
        this.terminating = terminating;
    }

}
