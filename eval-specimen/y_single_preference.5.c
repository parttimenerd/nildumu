/* copied from the ApproxFlow repo, which in turn is based on */
/* F. Biondi, A. Legay, and J. Quilbeuf, “Comparative analysis of leakage tools on scalable case studies,” in Lecture Notes in Computer Science (including subseries Lecture Notes in Artificial Intelligence and Lecture Notes in Bioinformatics), 2015, vol. 9232, pp. 263–281. */

#define CONST_N 5
#define CONST_C 5

int N;

// C is the number of candidates
int C;


//factorial function
int fact(int n){
  if (n <= 1) 
    return 1;
  else
    return n*fact(n-1);
}

int main(int argc, char ** args){
  // global declarations

  // initialize public values
  N=5;
  C=5;

  // the result is the number of votes of each candidate
  int result[C]; // these are our public (observable) outputs
  for (int i=0 ; i<C ; i++) result[i] = 0;
  
  
  // init private values in the intervals defined in QUAIL file

  // The secret is the preference of each voter
  int vote[N]; // these are our secrets
  int CFACT= fact(C);
    vote[0] = INPUT(int) % CFACT;
    vote[1] = INPUT(int) % CFACT;
    vote[2] = INPUT(int) % CFACT;
    vote[3] = INPUT(int) % CFACT;
    vote[4] = INPUT(int) % CFACT;

    int i = 0;
    int j  = 0;
    while (i < N) {
      j=0;
      while (j < C) {
	if (vote[i] == j) {
	  result[j] = result[j] + 1 ;
	}
	j= j+1;
      }
      i= i + 1;
    }

      OBSERVE(result[0]);
      OBSERVE(result[1]);
      OBSERVE(result[2]);
      OBSERVE(result[3]);
      OBSERVE(result[4]);
}
