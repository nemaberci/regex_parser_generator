package hu.nemaberci.generator.nfa.data;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class AutomataState {

    private static int currId = 0;
    int id;
    Map<Character, AutomataState> nextStates = new HashMap<>();
    boolean terminating;
    public AutomataState() {
        this(currId++);
    }
    public AutomataState(int id) {
        this(id, false);
    }
    public AutomataState(int id, boolean terminating) {
        this.id = id;
        this.terminating = terminating;
    }

}
