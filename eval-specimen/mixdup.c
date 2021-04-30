/* J. Newsome, S. McCamant, and D. Song, Measuring Channel Capacity to Distinguish Undue Influence, in Proceedings of the ACM SIGPLAN Fourth Workshop on Programming Languages and Analysis for Security, 2009, pp. 73-85.*/
/* Should leak 16 bits */

int nondet();

int main() {
    int x = nondet();
    int y = ( ( x >> 16 ) ^ x ) & 0xffff;
    int O = y | ( y << 16 );
    return O;
}