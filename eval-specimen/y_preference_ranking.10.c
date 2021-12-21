/* copied from the ApproxFlow repo, which in turn is based on */
/* F. Biondi, A. Legay, and J. Quilbeuf, “Comparative analysis of leakage tools on scalable case studies,” in Lecture Notes in Computer Science (including subseries Lecture Notes in Artificial Intelligence and Lecture Notes in Bioinformatics), 2015, vol. 9232, pp. 263–281. */

#define CONST_N 10
#define CONST_C 10

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
  N=10;
  C=10;

  // the result is the number of votes of each candidate
  int result[C]; // these are our public (observable) outputs
  for (int i=0 ; i<C ; i++) result[i] = 0;
  
  
  // init private values in the intervals defined in QUAIL file

  // The secret is the preference of each voter
  int vote[N]; // these are our secrets
  int CFACT= fact(C);
  vote[0] = INPUT() % CFACT;
  vote[1] = INPUT() % CFACT;
  vote[2] = INPUT() % CFACT;
  vote[3] = INPUT() % CFACT;
  vote[4] = INPUT() % CFACT;
  vote[5] = INPUT() % CFACT;
  vote[6] = INPUT() % CFACT;
  vote[7] = INPUT() % CFACT;
  vote[8] = INPUT() % CFACT;
  vote[9] = INPUT() % CFACT;

  int voter = 0;
  int vote_val = 0;
  int decl[N]; //temporary table used by the QUAIL version
  // voting
  while (voter<N) {
    while (vote_val<CFACT) {
if (vote[voter]==vote_val) {
  decl[voter]=vote_val;
}  
vote_val=vote_val+1;
    }
    vote_val=0;
    voter=voter+1;
  }

  // transform the secret of each voter into the order of the preferences
  voter=0;
  while (voter<N) { 

    // build the initial array
    int candidate=0;
    int temparray[C];  //temporary table used by the QUAIL version
    while (candidate<C){
temparray[candidate]=candidate;
candidate=candidate+1;
    }

    int k=C;
    // find a position
    while (k>0) {
int pos = decl[voter]%k;
candidate=C-k;
// update the vote of the candidate
result[candidate]=result[candidate]+temparray[pos];

// remove the element from the array
int y=pos;
while (y<C-1) {
  temparray[y]=temparray[y+1];
  y=y+1;
}

// update the vote of the voter
decl[voter]=decl[voter]/k;

// decrease the counter
k=k-1;
    }
    voter=voter+1;
  }
    LEAK(result[0]);
    LEAK(result[1]);
    LEAK(result[2]);
    LEAK(result[3]);
    LEAK(result[4]);
    LEAK(result[5]);
    LEAK(result[6]);
    LEAK(result[7]);
    LEAK(result[8]);
    LEAK(result[9]);
}
