/* Toy program from paper of R. Chadha et. al "Computing information flow using symbolic model-checking"*/
/* Should leak 2 bit */

int main() {
  int H = INPUT(int);
  int O = 0;
  while (H >= 5 && H < 20){
      H = H - 5;
      O = O + 1;
  }
  LEAK(O);
}
