/* Z. Meng and G. Smith, Calculating Bounds on Information Leakage Using Two-bit Patterns, in Proceedings of the ACM SIGPLAN 6th Workshop on Programming Languages and Analysis for Security, 2011, p. 1:1--1:12. */
/* Should leak 16 bits */

int main() {
    LEAK(INPUT(int) & 0xffff0000);
}