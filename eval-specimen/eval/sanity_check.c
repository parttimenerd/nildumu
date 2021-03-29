/* Z. Meng and G. Smith, Calculating Bounds on Information Leakage Using Two-bit Patterns, in Proceedings of the ACM SIGPLAN 6th Workshop on Programming Languages and Analysis for Security, 2011, p. 1:1--1:12. */
/* Should leak 4 bits */
int nondet();
int main() {
    int S = nondet();
    int O = 0;
    int base = 0x7ffffffa;
    if (S < 16){
        O = base + S;
    } else {
        O = base;
    }
    return O;
}