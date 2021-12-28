/* J. Newsome, S. McCamant, and D. Song, Measuring Channel Capacity to Distinguish Undue Influence, in Proceedings of the ACM SIGPLAN Fourth Workshop on Programming Languages and Analysis for Security, 2009, pp. 73-85.*/
/* Should leak log(33) = 5.0444 bits */

int main() {
    int h = INPUT(int);
    int i = (h & 0b01010101010101010101010101010101) + ((h >> 1) & 0b01010101010101010101010101010101);
    i = (i & 0b00110011001100110011001100110011) + ((i >> 2) & 0b00110011001100110011001100110011);
    i = (i & 0b00001111000011110000111100001111) + ((i >> 4) & 0b00001111000011110000111100001111);
    i = (i & 0b00000000111111110000000011111111) + ((i >> 8) & 0b00000000111111110000000011111111);
    OBSERVE((i + (i >> 16)) & 0b1111111111111111);
}