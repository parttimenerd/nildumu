/* Z. Meng and G. Smith, Calculating Bounds on Information Leakage Using Two-bit Patterns, in Proceedings of the ACM SIGPLAN 6th Workshop on Programming Languages and Analysis for Security, 2011, p. 1:1--1:12. */
/* Should leak 16 bits */
/* from ApproxFlow source code */
#define BITS 32
int nondet_uint(void);


int main() {
    unsigned int I = nondet_uint();

    unsigned int O = 0;

    unsigned int m;
    int i;

    for (i = 0; i < BITS; i++) {
      m = 1<<(31-i);
      if (O + m <= I) O += m;
    }
    return O;
}