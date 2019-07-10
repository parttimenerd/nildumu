package nildumu;

import org.junit.jupiter.api.*;

import java.util.HashSet;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import swp.util.Pair;

import static nildumu.Lattices.BasicSecLattice.*;
import static nildumu.Lattices.bl;
import static nildumu.Lattices.bs;
import static nildumu.util.Util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all the basic lattices. These lattices are the foundation of the whole analysis, they should
 * work as intended.
 */
public class LatticesTest {

    @Nested
    public class TestSecLattice {
        @Test
        public void testBasicSecurityLattice(){
            assertParsingCorrect(HIGH, p("h", HIGH));
        }

        @Test
        public void testSup(){
            assertEquals(HIGH,
                    Lattices.BasicSecLattice.get().sup(HIGH, LOW));
            assertEquals(LOW, Lattices.BasicSecLattice.get().sup(Stream.of(LOW, LOW)));
            assertEquals(LOW, Lattices.BasicSecLattice.get().sup(Stream.of()));
        }

        @Test
        public void testInf(){
            assertEquals(LOW,
                    Lattices.BasicSecLattice.get().inf(HIGH, LOW));
            assertEquals(HIGH, Lattices.BasicSecLattice.get().inf(Stream.of()));
        }

        @Test
        public void testIn(){
            assertTrue(Lattices.BasicSecLattice.get().in(HIGH, HIGH, LOW));
        }
    }

    @Nested
    public class TestSetLattice {
        @Test
        public void testParsing(){
            assertParsingCorrect(new Lattices.SetLattice<>(Lattices.BasicSecLattice.get(), c -> {
                        HashSet<Lattices.BasicSecLattice> h = new HashSet<>(c);
                        return h;
                    }),
            p("{}", set()),
                    p("{ h }", set(HIGH)),
                    p("{h, l}", set(HIGH, LOW)));
            assertEquals(set(HIGH), new Lattices.SetLattice<>(Lattices.BasicSecLattice.get(), c -> {
                HashSet<Lattices.BasicSecLattice> h = new HashSet<>(c);
                return h;
            }).parse("{#bla}", x -> HIGH));
        }
    }

    @Nested
    public class ValueLatticeTest {
        @Test
        public void testParsing(){
            assertParsingCorrect(Lattices.ValueLattice.get(), Lattices.Value::valueEquals,
                    p("0b00", new Lattices.Value(bl.create(Lattices.B.ZERO), bl.create(Lattices.B.ZERO))));
        }
    }

    @SafeVarargs
    final <T> void assertParsingCorrect(Lattices.Lattice<T> lattice, BiPredicate<T, T> eq, Pair<String, T>... pairs){
        for (Pair<String, T> pair : pairs) {
            T actual = lattice.parse(pair.first);
            assertTrue(eq.test(actual, pair.second), String.format("Expected '%s', got '%s'", lattice.toString(pair.second), lattice.toString(actual)));
        }
    }

    @SafeVarargs
    final <T> void assertParsingCorrect(Lattices.Lattice<T> lattice, Pair<String, T>... pairs){
        assertParsingCorrect(lattice, Object::equals, pairs);
    }

    @ParameterizedTest
    @CsvSource({"u, u, u", "u, e, n", "s, e, s", "s, n, s", "s, u, s"})
    public void testBitValueLatticeSup(String first, String second, String expectedSupremum){
        assertEquals(bs.parse(expectedSupremum), bs.sup(bs.parse(first), bs.parse(second)));
    }

    @ParameterizedTest
    @CsvSource({"u, u, 1", "u, e, 0"})
    public void testBitValueLatticeLowerEquals(String first, String second, int expectedResult){
        assertEquals(expectedResult == 1, bs.lowerEqualsThan(bs.parse(first), bs.parse(second)));
    }
}