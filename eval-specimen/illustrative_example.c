/* Z. Meng and G. Smith, Calculating Bounds on Information Leakage Using Two-bit Patterns, in Proceedings of the ACM SIGPLAN 6th Workshop on Programming Languages and Analysis for Security, 2011, p. 1:1--1:12. */
/* Should leak log 17 = 4.087 bits */
/* adapted from ApproxFlow source code */

int nondet();

int main() {
  int O;
  int S = nondet();
  S = S & 0x77777777;
  if (S <= 64) O = S;
    else O = 0;
  if (O % 2 == 0)
    O++;
  return O;
}
