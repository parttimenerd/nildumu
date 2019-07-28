package nildumu.intervals;

import java.util.*;
import java.util.stream.*;

import nildumu.Lattices;

import static nildumu.Lattices.B.*;
import static nildumu.Lattices.*;

public class Intervals {

  static int bitVal(int val, int index) {
    if (index == vl.bitWidth - 1) {
      return val >= 0 ? 0 : 1;
    }
    return (val & (1 << index)) >> index;
  }

  public static int countPattern(Interval interval, Constraints constraints) {
    return countPattern(interval.start, interval.end, constraints);
  }

  public static int countPattern(int a, int b, Constraints constraints) {
    return new PatternCounterM().countPattern(a, b, constraints);
  }

  public static interface Constraints {

    default String stringify() {
      return IntStream.range(0, size()).mapToObj(i -> get(size() - i - 1).toString()).collect(Collectors.joining());
    }

    default boolean match(int val) {
      for (int i = 0; i < size(); i++) {
        Lattices.B b = get(i);
        if (b != U && bitVal(val, i) != b.value.get()) {
          return false;
        }
      }
      return true;
    }

    default int highestBitThatDoesNotMatch(int val) {
      for (int i = size(); i >= 0; i--) {
        Lattices.B b = get(i);
        if (b != U && bitVal(val, i) != b.value.get()) {
          return i;
        }
      }
      return -1;
    }

    Lattices.B get(int index);

    int size();

    default Constraints negate() {
      return new Constraints() {
        @Override
        public Lattices.B get(int index) {
          return Constraints.this.get(index).neg();
        }

        @Override
        public int size() {
          return Constraints.this.size();
        }
      };
    }
  }

  public static class ConstrainedInterval extends Interval {

    public final Interval interval;

    public final Map<Integer, Lattices.B> constraints;

    public ConstrainedInterval(Interval interval, Map<Integer, Lattices.B> constraints) {
      super(interval.start, interval.end);
      this.interval = interval;
      this.constraints = constraints;
    }

    public ConstrainedInterval(Interval interval) {
      this(interval, new HashMap<>());
    }

    public static ConstrainedInterval parse(String str) {
      String[] parts = str.split("\\|");
      Map<Integer, Lattices.B> constraints = new HashMap<>();
      if (parts.length == 3) {
        String reversed = new StringBuilder(parts[2]).reverse().toString();
        IntStream.range(0, vl.bitWidth - 1).forEach(i -> {
          if (i < reversed.length()) {
            constraints.put(i, bs.parse(reversed.substring(i, i + 1)));
          }
        });
      }
      return new ConstrainedInterval(new Interval(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])), constraints);
    }

    public void constrain(int index, Lattices.B b) {
      constraints.put(index, b);
    }

    public Lattices.B constrain(int index) {
      return constraints.getOrDefault(index, U);
    }

    @Override
    public String toString() {
      return String.format("[%d, %d, {%s}]", start, end, IntStream.range(0, vl.bitWidth)
              .mapToObj(i -> constrain(vl.bitWidth - i - 1).toString()).collect(Collectors.joining("")));
    }

    public Lattices.B signConstraint() {
      return constrain(vl.bitWidth - 1);
    }

    public int size() {
      Constraints constraints = new ListConstraints(this.constraints);
      return (int) IntStream.range(start, end + 1).filter(constraints::match).count();
    }
  }

  public static class ListConstraints implements Constraints {
    public final int size;
    private final List<Lattices.B> constraints;

    ListConstraints(Map<Integer, Lattices.B> constraints) {
      this(IntStream.range(0, vl.bitWidth).mapToObj(i -> constraints.getOrDefault(i, U))
              .collect(Collectors.toList()));
    }

    ListConstraints(List<Lattices.B> constraints) {
      this(constraints, (int) constraints.stream().filter(b -> b.isConstant()).count());
    }

    ListConstraints(List<Lattices.B> constraints, int size) {
      this.constraints = constraints;
      this.size = size;
    }

    public Lattices.B get(int i) {
      return constraints.get(i);
    }

    @Override
    public String toString() {
      return stringify();
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public Constraints negate() {
      return new ListConstraints(constraints.stream().map(Lattices.B::neg).collect(Collectors.toList()));
    }
  }

  public static abstract class PatternCounter {

    public abstract int countPattern(int a, int b, Constraints constraints);

  }

  public static class PatternCounterM extends PatternCounter {

    @Override
    public int countPattern(int a, int b, Constraints constraints) {
      if (a == 0) {
        if (b >= 0) {
          return countPatternZeroIncl(b, constraints);
        } else {
          return countPatternZeroIncl(-b, constraints.negate());
        }
      }
      if (a > 0) {
        return countPatternZeroIncl(b, constraints) - countPatternZero(a, constraints);
      } else {
        if (b >= 0) {
          return countPatternZeroIncl(b, constraints) + countPatternZeroIncl(~a, constraints.negate()); //- (constraints.negate().match(0) ? 1 : 0);
        }
        return countPattern(~b, ~a, constraints.negate());
      }
    }

    public int countPatternZeroIncl(int b, Constraints constraints) {
      return countPatternZero(b, constraints) + (constraints.match(b) ? 1 : 0);
    }

    public int countPatternZero(int b, Constraints constraints) {
      int solution = 0;
      int highestUnequalIndex = constraints.highestBitThatDoesNotMatch(b);
      for (int i = vl.bitWidth - 1; i >= 0; i--) {
        Lattices.B m = constraints.get(i);
        int a = bitVal(b, i);
        if (a == 1 && m == ZERO) {
          if (i + 1 > highestUnequalIndex) {
            solution += 1;
          }
        }
        if (a == 0 && m == U) {
          solution = 2 * solution;
        }
        if (a == 1 && m == U) {
          if (i + 1 > highestUnequalIndex) {
            solution = 2 * solution + 1;
          } else {
            solution = 2 * solution;
          }
        }
      }
      return solution;
    }
  }
}
