#include <time.h>
#include <stdlib.h>
#include <stdio.h>
//#include <string.h>

// N is the total number of houses 
int N;

// indicates the size of the target. Only one of those should be one and all the other 0.
int target_is_S;
int target_is_M;
int target_is_L;

// We consider different sizes of houses. S, M and L indicate the number of houses of each size.
int S;
int M;
int L;

// each size correspond to a different level of consumption
int  small_consumption;
int medium_consumption;
int  large_consumption;

// the observable is the global consumption of the system
int global_consumption;

// the secret is the presence 
int presence_target;

// e.g. case1 or case2 from the paper
int case_value;

void error(){
  //printf("Usage: GuessPresenceSingleHouse <nb_houses> <target type> [case1|case2]\n");
  //printf("<target type> is either S M or L\n");
  exit(1);
}

void numberOfEach(){
  S=N/3 - target_is_S ;
  M=N/3 - target_is_M ;
  L=N/3 - target_is_L ;
}

extern int presence[16];

int main() {
  N=3;

  // indicates the size of the target. Only one of those should be one and all the other 0.
  target_is_S = 1 ;
  target_is_M = 0 ;
  target_is_L = 0 ;

  // We consider different sizes of houses. S, M and L indicate the number of houses of each size.
  S=N/3 - target_is_S ;
  M=N/3 - target_is_M ;
  L=N/3 - target_is_L ;

  // each size correspond to a different level of consumption
  small_consumption = 1 ;
  medium_consumption = 3 ;
  large_consumption = 5 ;
  
  // the observable is the global consumption of the system
  global_consumption = 0;
  
  
  srand(time(NULL));

  
  
  // Initialize the public values
  N = 16;
  S=N/3 ;
  M=N/3 ;
  L=N-S-M ;
  case_value = 1;

      if (case_value == 1) {
  small_consumption = 1 ;
  medium_consumption = 2 ;
  large_consumption = 3 ;
      }
      else {
  small_consumption = 1 ;
  medium_consumption = 3 ;
  large_consumption = 5 ;
      }
    char CONST_MODE = 'L';
    if (CONST_MODE == 'L'){
      target_is_S = 0 ;
      target_is_M = 0 ;
      target_is_L = 1 ;
    } else if (CONST_MODE == 'M'){
      target_is_S = 0 ;
      target_is_M = 1 ;
      target_is_L = 0 ;
    } else if (CONST_MODE == 'S'){

      target_is_S = 1 ;
      target_is_M = 0 ;
      target_is_L = 0 ;
    } else {
      error();
    }
    /* inlined call to numberOfEach() */
    S=N/3 - target_is_S;
    M=N/3 - target_is_M;
    L=N/3 - target_is_L;

  // Done initializing the public values
  
  // init private values in the intervals defined in QUAIL�file
  // Initialize the private values
  // YES, I KNOW THIS IS HORRIBLE AND NOT SECURE OR IN THE SPIRIT OF THE ORIGINAL JAVA CODE, USING rand() CALLS
  // TODO: Don't use rand, use the equivalent of the Java Code's SecureRandom (which was in the original implementation)
  
  //  the presence of people in each house
    int u;
    for(u =0; u < N; u++){
      presence[u]=presence[u] & 1;
    }
  presence_target=rand(); // presence_target is the secret

  // Done initializing the private values
  
  
  

    
    if  (presence_target == 1) {
      if  (target_is_S == 1) {
	global_consumption = global_consumption + small_consumption ;
      } 
      else if (target_is_M == 1) {
	global_consumption = global_consumption + medium_consumption ;
      }
      else {
	global_consumption= global_consumption + large_consumption ;
      }
    }
    
    int i  = 0;
    while ( i < N-1) {
      if (presence[i] == 1) {
        
	if (i<S) {
	  global_consumption = global_consumption + small_consumption ;
	}
	else if (i<S+M) {
	  global_consumption = global_consumption + medium_consumption ;
	}
	else{
	  global_consumption = global_consumption + large_consumption ;
	}
      }
      i= i + 1;
    }
    // global_consumption is the public value (output)
    int result = global_consumption;
        __CPROVER_assert(0, "ret-val assertion");
}
