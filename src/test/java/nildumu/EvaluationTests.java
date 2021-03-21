package nildumu;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static nildumu.LoopTests.parse;

/** these tests are exactly the tests used during evaluation (but with less inlinings) */
public class EvaluationTests {

    @BeforeAll
    public static void load() { parse("int i = 0"); }

    @Test
    public void testElectronicPurse(){
        check("h input int H = 0bu{32};\n" +
                "int O = 0;\n" +
                "while (H >= 5 && H < 20){\n" +
                "    H = H - 5;\n" +
                "    O = O + 1;\n" +
                "}\n" +
                "l output int o = O;", 5, 5);
    }

    @Test
    public void testElectronicPurse2(){ // modified
        check("h input int H = 0bu{32};\n" +
                "int O = 0;\n" +
                "while (H >= 5 && H < 20){\n" +
                "    H = H - 1;\n" +
                "    O = O + 1;\n" +
                "}\n" +
                "l output int o = O;", 5, 5);
    }

    @Test
    public void testBinarySearch() {
        check("h input int I = 0b0u{32};\n" +
                "\n" +
                "int BITS = 16;\n" +
                "\n" +
                "int z = 0;\n" +
                "\n" +
                "int i = 0;\n" +
                "\n" +
                "while (i < 16){\n" +
                "    int m = 1<<(31-i);\n" +
                "    if (z + m <= I) {\n" +
                "        z = z + m;\n" +
                "    }\n" +
                "    i = i + 1;\n" +
                "}\n" +
                "l output int o = z;", 16, 32);
    }

    @Test
    public void testIllustrativeExample() {
        check("/* Z. Meng and G. Smith, Calculating Bounds on Information Leakage Using Two-bit Patterns, in Proceedings of the ACM SIGPLAN 6th Workshop on Programming Languages and Analysis for Security, 2011, p. 1:1--1:12. */\n" +
                "/* Should leak log 17 = 4.087 bits */\n" +
                "int O;\n" +
                "h input int h = 0bu{32};\n" +
                "int z = h & 0b01110111011101110111011101110111; /* 0x77777777 */\n" +
                "if (z <= 64) {\n" +
                "    O = h;\n" +
                "} else {\n" +
                "    O = 0;\n" +
                "}\n" +
                "if (O % 2 == 0) {\n" +
                "    O = O + 1;\n" +
                "}\n" +
                "l output int o = O;", 13, 13);
    }

    @Test
    public void testIllustrativeExample2() {
        check("/* Z. Meng and G. Smith, Calculating Bounds on Information Leakage Using Two-bit Patterns, in Proceedings of the ACM SIGPLAN 6th Workshop on Programming Languages and Analysis for Security, 2011, p. 1:1--1:12. */\n" +
                "/* Should leak log 17 = 4.087 bits */\n" +
                "int O;\n" +
                "h input int h = 0b0u{32};\n" +
                "int z = h & 0b001110111011101110111011101110111; /* 0x77777777 */\n" +
                "if (z <= 64) {\n" +
                "    O = h;\n" +
                "} else {\n" +
                "    O = 0;\n" +
                "}\n" +
                "if (O % 2 == 0) {\n" +
                "    O = O + 1;\n" +
                "}\n" +
                "l output int o = O;", 9, 9);
    }

    @Test
    public void testImplicitFlow() {
        check("/* Toy program from paper of Meng et. al: \"Calculating bounds on information leakage using two-bit patterns\" */\n" +
                "/* https://github.com/qif/jpf-qif/blob/master/src/examples/plas/ImplicitFlow.java */\n" +
                "/* Should leak log 7 = 2.8074 */\n" +
                "\n" +
                "h input int S = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "int O;\n" +
                "if (S == 0) {\n" +
                "    O = 0;\n" +
                "} else {\n" +
                "    if (S == 1) {\n" +
                "       O = 1;\n" +
                "    } else {\n" +
                "        if (S == 2) {\n" +
                "            O = 2;\n" +
                "        } else {\n" +
                "            if (S == 3) {\n" +
                "                O = 3;\n" +
                "            } else {\n" +
                "                if (S == 4) {\n" +
                "                    O = 4;\n" +
                "                } else {\n" +
                "                    if (S == 5) {\n" +
                "                        O = 5;\n" +
                "                    } else {\n" +
                "                        if (S == 6) {\n" +
                "                            O = 6;\n" +
                "                        } else {\n" +
                "                            O = 0;\n" +
                "                        }\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "l output int o = O;", 3, 3);
    }

    @Test
    public void testMaskedCopy() {
        check("/* Z. Meng and G. Smith, Calculating Bounds on Information Leakage Using Two-bit Patterns, in Proceedings of the ACM SIGPLAN 6th Workshop on Programming Languages and Analysis for Security, 2011, p. 1:1--1:12. */\n" +
                "/* Should leak 16 bits */\n" +
                "h input int S = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "l output int o = S & 0b11111111111111110000000000000000; /*0xffff0000*/", 16, 16);
    }

    @Test
    public void testMixDup() {
        check("/*[1] J. Newsome, S. McCamant, and D. Song, Measuring Channel Capacity to Distinguish Undue Influence, in Proceedings of the ACM SIGPLAN Fourth Workshop on Programming Languages and Analysis for Security, 2009, pp. 73-85.*/\n" +
                "/* Should leak 16 bits */\n" +
                "h input int x = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "int y = ( ( x >> 16 ) ^ x ) & 0b00000000000000001111111111111111;\n" +
                "int O = y | ( y << 16 );\n" +
                "l output int o = O;", 16, 16);
    }

    @Test
    public void testPopulationCount() {
        check("/* J. Newsome, S. McCamant, and D. Song, Measuring Channel Capacity to Distinguish Undue Influence, in Proceedings of the ACM SIGPLAN Fourth Workshop on Programming Languages and Analysis for Security, 2009, pp. 73-85.*/\n" +
                "/* Should leak log(33) = 5.0444 bits */\n" +
                "h input int h = 0b0uuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "int i = (h & 0b001010101010101010101010101010101) + ((h >> 1) & 0b001010101010101010101010101010101);\n" +
                "i = (i & 0b000110011001100110011001100110011) + ((i >> 2) & 0b000110011001100110011001100110011);\n" +
                "i = (i & 0b000001111000011110000111100001111) + ((i >> 4) & 0b00001111000011110000111100001111);\n" +
                "i = (i & 0b000000000111111110000000011111111) + ((i >> 8) & 0b000000000111111110000000011111111);\n" +
                "l output int o = (i + (i >> 16)) & 0b1111111111111111;\n", 10, 10);
    }

    @Test
    public void testPasswordChecker() {
        check("/* Typical password checker adaption, that compares with \"1\", as not all tools support */\n" +
                "/* low inputs */\n" +
                "h input int h = 0bu{32};\n" +
                "l input int l = 0;\n" +
                "if (h == l){\n" +
                "    l = 1;\n" +
                "} else {\n" +
                "    l = 0;\n" +
                "}\n" +
                "l output int o = l;", 1, 1);
    }

    @Test
    public void testSanityCheck() {
        check("use_sec basic;\n" +
                "bit_width 32;\n" +
                "h input int S = 0bu{32};\n" +
                "int O = 0;\n" +
                "int base = 0b01111{24}1010; /* 0x7ffffffa; */\n" +
                "if ((S < 16))\n" +
                "  {\n" +
                "    O = (base + S);\n" +
                "  } \n" +
                "else\n" +
                "  {\n" +
                "    O = base;\n" +
                "  }\n" +
                "l output int o = O;\n", 32, 32);
    }

    @Test
    public void testSanityCheck2() {
        check("use_sec basic;\n" +
                "bit_width 32;\n" +
                "h input int S = 0bu{32};\n" +
                "int O = 0;\n" +
                "int base = 0b0{16}00010{12};\n" +
                "if ((S < 16))\n" +
                "  {\n" +
                "    O = (base + S);\n" +
                "  } \n" +
                "else\n" +
                "  {\n" +
                "    O = base;\n" +
                "  }\n" +
                "l output int o = O;\n", 32, 32);
    }

    @Test
    public void testSum() {
        check("/* Toy program from paper of Backes et. al: \"Automatic */\n" +
                "/* discovery and quantification of information leaks\" */\n" +
                "/* Should leak n */\n" +
                "h input int x = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "h input int y = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "h input int z = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "l output int o = x + y + z;", 32, 32);
    }

    @Test
    public void testLaunderingAttack() {
        check("h input int h = 0bu{32}; int O = 0;\n" +
                "while (O != (h | 0b01)) {\n" +
                "O = O + 1;\n" +
                "} l output int o = O;", 31, 31);
    }

    @Test
    public void testFibonnacci() {
        check("/* Should leak five bit */\n" +
                "int fib(int num){\n" +
                "    int r = 1;\n" +
                "    if (num > 2){\n" +
                "        r = fib(num - 1) + fib(num - 2);\n" +
                "    }\n" +
                "    return r;\n" +
                "}\n" +
                "\n" +
                "h input int h = 0bu{32};\n" +
                "int z = fib(h);\n" +
                "l output int o = z;", -1, 32);
    }

    @Test
    public void testGuessPresenceAll() {
        check("/* N is the total number of houses */\n" +
                "int N;\n" +
                "\n" +
                "/* We consider different sizes of houses. S, M and L indicate the number of houses of each size. */\n" +
                "int S;\n" +
                "int M;\n" +
                "int L;\n" +
                "\n" +
                "/* each size correspond to a different level of consumption */\n" +
                "int  small_consumption;\n" +
                "int medium_consumption;\n" +
                "int  large_consumption;\n" +
                "\n" +
                "/* the observable is the global consumption of the system */\n" +
                "int global_consumption;\n" +
                "\n" +
                "/* the secret is the presence */\n" +
                "h input int presence_target = 0bu{32};\n" +
                "\n" +
                "/* e.g. case1 or case2 from the paper */\n" +
                "int case_value;\n" +
                "\n" +
                "N=3;\n" +
                "\n" +
                "/* We consider different sizes of houses. S, M and L indicate the number of houses of each size. */\n" +
                "S=N/3;\n" +
                "M=N/3;\n" +
                "L=N-S-M;\n" +
                "\n" +
                "/* each size correspond to a different level of consumption */\n" +
                "small_consumption = 1 ;\n" +
                "medium_consumption = 3 ;\n" +
                "large_consumption = 5 ;\n" +
                "\n" +
                "/* the observable is the global consumption of the system */\n" +
                "global_consumption = 0;\n" +
                "\n" +
                "/* Initialize the public values */\n" +
                "N = 64; /*  a valid value for the test case, note in paper */\n" +
                "S=N/3 ;\n" +
                "M=N/3 ;\n" +
                "L=N-S-M ;\n" +
                "case_value = 1; /* also use case = 0 */\n" +
                "\n" +
                " if (case_value == 1) {\n" +
                "small_consumption = 1 ;\n" +
                "medium_consumption = 2 ;\n" +
                "large_consumption = 3 ;\n" +
                " }\n" +
                " else {\n" +
                "small_consumption = 1 ;\n" +
                "medium_consumption = 3 ;\n" +
                "large_consumption = 5 ;\n" +
                " }\n" +
                "/* Done initializing the public values */\n" +
                "\n" +
                "h input int[64] presence = 0b00u;  /* the secret is an array of bools */\n" +
                "\n" +
                "int i  = 0;\n" +
                "while ( i < N ) {\n" +
                " if ((presence[i]) == 1) {\n" +
                "   \n" +
                "if (i<S) {\n" +
                "  global_consumption = global_consumption + small_consumption ;\n" +
                "}\n" +
                "else { if (i<S+M) {\n" +
                "  global_consumption = global_consumption + medium_consumption ;\n" +
                "}\n" +
                "else{\n" +
                "  global_consumption = global_consumption + large_consumption ;\n" +
                "} \n" +
                "}\n" +
                " }\n" +
                " i= i + 1;\n" +
                "}\n" +
                "l output int out = global_consumption;", 32, 32);
    }

    @Test
    public void testGuessPresenceSingleHouse() {
        check("/* N is the total number of houses */\n" +
                "int N;\n" +
                "\n" +
                "/* indicates the size of the target. Only one of those should be one and all the other 0. */\n" +
                "int target_is_S;\n" +
                "int target_is_M;\n" +
                "int target_is_L;\n" +
                "\n" +
                "/* We consider different sizes of houses. S, M and L indicate the number of houses of each size. */\n" +
                "int S;\n" +
                "int M;\n" +
                "int L;\n" +
                "\n" +
                "/* each size correspond to a different level of consumption */\n" +
                "int  small_consumption;\n" +
                "int medium_consumption;\n" +
                "int  large_consumption;\n" +
                "\n" +
                "/* the observable is the global consumption of the system */\n" +
                "int global_consumption;\n" +
                "\n" +
                "/* the secret is the presence */\n" +
                "h input int presence_target = 0bu{32};\n" +
                "\n" +
                "/* e.g. case1 or case2 from the paper */\n" +
                "int case_value;\n" +
                "\n" +
                "N=3;\n" +
                "\n" +
                "/* indicates the size of the target. Only one of those should be one and all the other 0. */\n" +
                "target_is_S = 1 ;\n" +
                "target_is_M = 0 ;\n" +
                "target_is_L = 0 ;\n" +
                "\n" +
                "\n" +
                "/* We consider different sizes of houses. S, M and L indicate the number of houses of each size. */\n" +
                "S=N/3 - target_is_S;\n" +
                "M=N/3 - target_is_M;\n" +
                "L=N/3 - target_is_L;\n" +
                "\n" +
                "/* each size correspond to a different level of consumption */\n" +
                "small_consumption = 1 ;\n" +
                "medium_consumption = 3 ;\n" +
                "large_consumption = 5 ;\n" +
                "\n" +
                "/* the observable is the global consumption of the system */\n" +
                "global_consumption = 0;\n" +
                "\n" +
                "/* Initialize the public values */\n" +
                "N = 64; /*  a valid value for the test case, note in paper */\n" +
                "S=N/3 ;\n" +
                "M=N/3 ;\n" +
                "L=N-S-M ;\n" +
                "case_value = 1; /* also use case = 0 */\n" +
                "\n" +
                " if (case_value == 1) {\n" +
                "small_consumption = 1 ;\n" +
                "medium_consumption = 2 ;\n" +
                "large_consumption = 3 ;\n" +
                " }\n" +
                " else {\n" +
                "small_consumption = 1 ;\n" +
                "medium_consumption = 3 ;\n" +
                "large_consumption = 5 ;\n" +
                " }\n" +
                "int CONST_MODE = 0;\n" +
                "if (CONST_MODE == 0){\n" +
                " target_is_S = 0 ;\n" +
                " target_is_M = 0 ;\n" +
                " target_is_L = 1 ;\n" +
                "} else{ if (CONST_MODE == 1){\n" +
                " target_is_S = 0 ;\n" +
                " target_is_M = 1 ;\n" +
                " target_is_L = 0 ;\n" +
                "} else { if (CONST_MODE == 2){\n" +
                "\n" +
                " target_is_S = 1 ;\n" +
                " target_is_M = 0 ;\n" +
                " target_is_L = 0 ;\n" +
                "}\n" +
                "}\n" +
                "}\n" +
                "/* inlined call to numberOfEach() */\n" +
                "S=N/3 - target_is_S;\n" +
                "M=N/3 - target_is_M;\n" +
                "L=N/3 - target_is_L;\n" +
                "\n" +
                "/* Done initializing the public values */\n" +
                "\n" +
                "h input int[64] presence = 0b00u;  /* the secret is an array of bools */\n" +
                "\n" +
                "/* done initializing the values */\n" +
                "\n" +
                " if  (presence_target == 1) {\n" +
                " if  (target_is_S == 1) {\n" +
                "global_consumption = global_consumption + small_consumption ;\n" +
                " } \n" +
                " else { if (target_is_M == 1) {\n" +
                "global_consumption = global_consumption + medium_consumption ;\n" +
                " }\n" +
                " else {\n" +
                "global_consumption= global_consumption + large_consumption ;\n" +
                " }\n" +
                "}\n" +
                " }\n" +
                "\n" +
                "int i  = 0;\n" +
                "while ( i < N - 1) {\n" +
                " if (presence[i] == 1) {\n" +
                "   \n" +
                "if (i<S) {\n" +
                "  global_consumption = global_consumption + small_consumption ;\n" +
                "}\n" +
                "else { if (i<S+M) {\n" +
                "  global_consumption = global_consumption + medium_consumption ;\n" +
                "}\n" +
                "else{\n" +
                "  global_consumption = global_consumption + large_consumption ;\n" +
                "}\n" +
                "}\n" +
                " }\n" +
                " i= i + 1;\n" +
                "}\n" +
                "    \n" +
                "l output int out = global_consumption;", 32, 32);
    }

    @Test
    public void testPreferenceRanking() {
        check("int N = 10;\n" +
                "\n" +
                "/* C is the number of candidates */\n" +
                "int C = 10;\n" +
                "\n" +
                "\n" +
                "/* factorial function */\n" +
                "int fact(int n){\n" +
                "  int ret = 1;\n" +
                "  if (n <= 1) { \n" +
                "    ret = 1;\n" +
                "  } else {\n" +
                "    ret = n*fact(n - 1);\n" +
                "  }\n" +
                "  return ret;\n" +
                "}\n" +
                "  \n" +
                "/* the result is the number of votes of each candidate */\n" +
                "int[10 /* C */] result; /* these are our public (observable) outputs */\n" +
                "int i = 0;\n" +
                "while (i < C) {\n" +
                "\tresult[i] = 0;\n" +
                "\ti = i + 1;\n" +
                "}\n" +
                "\n" +
                "\n" +
                "  /* init private values in the intervals defined in QUAIL file */\n" +
                "\n" +
                "  /* The secret is the preference of each voter */\n" +
                "  h input int[10 /* N */] secrets = 0bu{32}; \n" +
                "  int[10 /* N */] vote; /* these are our secrets */\n" +
                "  int CFACT= fact(C);\n" +
                "  i = 0;\n" +
                "  while (i < N) {\n" +
                "    vote[i]=secrets[i]%CFACT;\n" +
                "    i = i + 1;\n" +
                "  }\n" +
                "\n" +
                "  int voter = 0;\n" +
                "  int vote_val = 0;\n" +
                "  int[10 /* N */] decl; /* temporary table used by the QUAIL version */\n" +
                "  /* voting */\n" +
                "  while (voter<N) {\n" +
                "    while (vote_val<CFACT) {\n" +
                "if (vote[voter]==vote_val) {\n" +
                "  decl[voter]=vote_val;\n" +
                "}  \n" +
                "vote_val=vote_val+1;\n" +
                "    }\n" +
                "    vote_val=0;\n" +
                "    voter=voter+1;\n" +
                "  }\n" +
                "\n" +
                "  /* transform the secret of each voter into the order of the preferences */\n" +
                "  voter=0;\n" +
                "  while (voter<N) { \n" +
                "\n" +
                "    /* build the initial array */\n" +
                "    int candidate=0;\n" +
                "    int[10 /* C */] temparray;  /* temporary table used by the QUAIL version */\n" +
                "    while (candidate<C){\n" +
                "temparray[candidate]=candidate;\n" +
                "candidate=candidate+1;\n" +
                "    }\n" +
                "\n" +
                "    int k=C;\n" +
                "    /* find a position */\n" +
                "    while (k>0) {\n" +
                "int pos = decl[voter]%k;\n" +
                "candidate=C-k;\n" +
                "/* update the vote of the candidate */\n" +
                "result[candidate]=(result[candidate])+(temparray[pos]);\n" +
                "\n" +
                "/* remove the element from the array */\n" +
                "int y=pos;\n" +
                "while (y<C - 1) {\n" +
                "  temparray[y]=temparray[y+1];\n" +
                "  y=y+1;\n" +
                "}\n" +
                "\n" +
                "/* update the vote of the voter */\n" +
                "decl[voter]=decl[voter]/k;\n" +
                "\n" +
                "/* decrease the counter */\n" +
                "k=k - 1;\n" +
                "    }\n" +
                "    voter=voter+1;\n" +
                "  }\n" +
                "\n" +
                "l output var out = result;", -1, -1);
    }

    @Test
    public void testSinglePreferenceRanking() {
        check("int N = 10;\n" +
                "\n" +
                "/* C is the number of candidates */\n" +
                "int C = 10;\n" +
                "\n" +
                "\n" +
                "/* factorial function */\n" +
                "int fact(int n){\n" +
                "  int ret = 1;\n" +
                "  if (n <= 1) { \n" +
                "    ret = 1;\n" +
                "  } else {\n" +
                "    ret = n*fact(n - 1);\n" +
                "  }\n" +
                "  return ret;\n" +
                "}\n" +
                "  \n" +
                "/* the result is the number of votes of each candidate */\n" +
                "int[10 /* C */] result; /* these are our public (observable) outputs */\n" +
                "int i = 0;\n" +
                "while (i < C) {\n" +
                "\tresult[i] = 0;\n" +
                "\ti = i + 1;\n" +
                "}\n" +
                "\n" +
                "\n" +
                "  /* init private values in the intervals defined in QUAIL file */\n" +
                "\n" +
                "  /* The secret is the preference of each voter */\n" +
                "  h input int[10 /* N */] secrets = 0bu{32}; \n" +
                "  int[10 /* N */] vote; /* these are our secrets */\n" +
                "  int CFACT= fact(C);\n" +
                "  i = 0;\n" +
                "  while (i < N) {\n" +
                "    vote[i]=secrets[i]%CFACT;\n" +
                "    i = i + 1;\n" +
                "  }\n" +
                "\n" +
                "  i = 0;\n" +
                "    int j  = 0;\n" +
                "    while (i < N) {\n" +
                "      j=0;\n" +
                "      while (j < C) {\n" +
                "\tif (vote[i] == j) {\n" +
                "\t  result[j] = (result[j]) + 1 ;\n" +
                "\t}\n" +
                "\tj= j+1;\n" +
                "      }\n" +
                "      i= i + 1;\n" +
                "    }\n" +
                "l output var out = result;", -1, -1);
    }

    public void check(String program, int shouldLeakAt32, int shouldLeakAt5) {
        if (shouldLeakAt32 != -1) {
            parse(program, 32).useSingleMCAlgo().leaks(shouldLeakAt32).run();
        }
        if (shouldLeakAt5 != -1) {
            parse(program, 5).useSingleMCAlgo().leaks(shouldLeakAt5).run();
        }
    }


    /*public static void main(String[] args) {
        new EvaluationTests().testElectronicPurse();
    }*/
}
