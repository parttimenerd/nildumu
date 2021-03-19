package nildumu.intervals;

import java.util.*;

import nildumu.Lattices;

import static nildumu.Lattices.vl;

/**
 * Interval with inclusive boundaries
 */
public class Interval {

    private static long counter = 0;
    public final long id;
    public long start;
    public long end;

    public final Set<Lattices.Bit> bits;

    public Interval(long start, long end) {
        this.id = counter++;
        this.start = start;
        this.end = end;
        bits = new HashSet<>();
    }

  public static Interval forBitWidth(int bitWidth) {
      return new Interval(- (2 << (bitWidth - 2)), (2 << (bitWidth - 2)) - 1);
  }

  @Override
    public String toString() {
        return String.format("[%d, %d]", start, end);
    }

    public long size(){
        return end - start + 1;
    }

    public boolean contains(int i){
        return i >= start && i <= end;
    }

  @Override
  public int hashCode() {
    return (int)id;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Interval && ((Interval) obj).id == id;
  }

  public boolean isDefaultInterval() {
      return start == - (2 << (vl.bitWidth - 2)) && end == (2 << (vl.bitWidth - 2)) - 1 && bits.isEmpty();
  }

  public Interval merge(Interval y) {
      return new Interval(Math.min(start, y.start), Math.max(end, y.end));
  }

  public Interval addBits(Collection<Lattices.Bit> bits){
      this.bits.addAll(bits);
      return this;
  }
}
