/* Toy program from paper of R. Chadha et. al "Computing information flow using symbolic model-checking"*/
/* Should leak 3 bit */

int nondet();

int main() {
  int H = nondet();
  int O = 0;
  while (H >= 5 && H < 20){
      H = H - 5;
      O = O + 1;
  }
  return O;
}
