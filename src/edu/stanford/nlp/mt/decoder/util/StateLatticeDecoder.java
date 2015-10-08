package edu.stanford.nlp.mt.decoder.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.util.MurmurHash2;

/**
 * A simple a-star based lattice decoder.
 * 
 * TODO(spenceg) The underlying agenda becomes enormous.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <S>
 */
public class StateLatticeDecoder<S extends State<S>> implements
    Iterator<List<S>>, Iterable<List<S>> {

  // Set empirically assuming n-best size of 200, the standard value for
  // tuning.
  private static final int DEFAULT_INITIAL_CAPACITY = 25000;
  
  private final PriorityQueue<CompositeState> agenda;
  private final RecombinationHistory<S> recombinationHistory;
  public int maxAgendaSize = 0; 

  /**
   * Constructor.
   * 
   * @param goalStates
   * @param recombinationHistory
   */
  public StateLatticeDecoder(List<S> goalStates, RecombinationHistory<S> recombinationHistory) {
    this.recombinationHistory = recombinationHistory;
    agenda = new PriorityQueue<>(DEFAULT_INITIAL_CAPACITY);
    
    // Initialize the agenda with list of goal nodes
    for (S goalState : goalStates) {
      assert goalState != null;
      agenda.add(new CompositeState(goalState));
    }
  }

  @Override
  public boolean hasNext() {
    return ! agenda.isEmpty();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<S> next() {
    final CompositeState best = agenda.poll();
    for (int i = 0, sz = best.states.length; i < sz; i++) {
      // Undo recombinations along the Viterbi path.
      final S currentState = (S) best.states[i];
      final List<S> recombinedStates = recombinationHistory.recombinations(currentState);
      for (S recombinedState : recombinedStates) {
        CompositeState newComposite = new CompositeState(best, recombinedState, i);
        agenda.add(newComposite);
      }
    }
    if (agenda.size() > maxAgendaSize) maxAgendaSize = agenda.size();
    return (List<S>) Arrays.asList(best.states);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  private class CompositeState implements Comparable<CompositeState> {
    public final State<S>[] states;
    private final double score;
    private final int hashCode;

    @Override
    public int hashCode() {
      return hashCode;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      else if ( ! (o instanceof StateLatticeDecoder.CompositeState)) return false;
      else {
        CompositeState oCS = (CompositeState) o;
        return score == oCS.score && // comparing "score" speeds up equals()
            Arrays.equals(states, oCS.states);
      }
    }

    @SuppressWarnings("unchecked")
    public CompositeState(S goalState) {
      final int length = goalState.depth() + 1;
      states = new State[length];
      int[] hashArr = new int[length];
      State<S> state = goalState;
      for (int i = length-1; i >= 0 && state != null; state = state.parent(), --i) {
        states[i] = state;
        hashArr[i] = state.hashCode();
      }
      score = goalState.partialScore();
      hashCode = MurmurHash2.hash32(hashArr, hashArr.length, 1);
    }
    
    /**
     * This method is counter-intuitive. In the case of multiple recombinations along
     * a single path, the parent pointers are invalid. So we need to sum transition costs
     * into each node on the lattice path.
     * 
     * @return
     */
    private double scorePath() {
      double cost = 0.0;
      for (State<S> state : states) {
        State<S> parent = state.parent();
        double parentScore = (parent == null ? 0 : parent.partialScore());
        cost += state.partialScore() - parentScore;
      }
      return cost;
    }

    @SuppressWarnings("unchecked")
    public CompositeState(CompositeState original, S varState, int varPosition) {
      final int newPrefixLength = varState.depth() + 1;
      final int length = original.states.length + newPrefixLength - varPosition - 1;
      states = new State[length];
      int[] hashArr = new int[length];
      State<S> newState = varState;
      for (int i = newPrefixLength - 1; i >= 0 && newState != null; newState = newState.parent(), --i) {
        states[i] = newState;
        hashArr[i] = newState.hashCode();
      }
      for (int i = varPosition + 1, sz = original.states.length, j = newPrefixLength; i < sz; i++, ++j) {
        assert j < states.length;
        states[j] = original.states[i];
        hashArr[j] = states[j].hashCode();
      }
      hashCode = MurmurHash2.hash32(hashArr, hashArr.length, 1);
      score = scorePath();
    }

    public double score() {
      return score;
    }

    @Override
    public int compareTo(CompositeState o) {
      return (int) Math.signum(o.score() - score());
    }

    @Override
    public String toString() {
      StringBuilder sbuf = new StringBuilder();
      for (State<S> state : states) {
        sbuf.append(state).append(",");
      }
      sbuf.append(String.format(" score: %.3f", score));
      return sbuf.toString();
    }
  }

  @Override
  public Iterator<List<S>> iterator() {
    return this;
  }
}
