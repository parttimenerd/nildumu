/* adapted from I. Sweet, J. M. C. Trilla, C. Scherrer, M. Hicks, and S. Magill, “What’s the Over/Under? Probabilistic Bounds on Information Leakage,” in Principles of Security and Trust, 2018, pp. 3–27. */
/* with S=8 ships and N=100 evacuees */

#include <stdio.h>
#include <stdlib.h>

short D = 10;
short N = 100;
short L[2] = {100, 100};

short berths[8][2];

short is_solution(short berths[8][2], short N) {
	short sum = 0;
	short i = 0;
	while (i < 8) {
		sum = sum + ((berths[i])[0]);
		i = i + 1;
	}
	return sum >= N;
}

short mid(short pos[2]) {
	return ((pos[0]) + (pos[1])) / 2;
}

short AtLeast(short Capacity[8], short z, short b) { return Capacity[z] >= b; }

short abs(short val) {
    return val > 0 ? val : -val;
}

short Nearby(short Loc[8][2], short z, short l[2], short d) {
	return abs((Loc[z][0]) - (l[0])) + abs((Loc[z][0]) - (l[0])) <= d;
}

extern short Capacity[8];
extern short Loc[8][2];

int main() {

    short i = 0;
    while (i < 8) {
        berths[i][0] = 0;
        berths[i][1] = 1000;
        i = i + 1;
    }
    while (1) {
        short i = 0;
        while (i < 8) {
            short ask = mid(berths[i]);
            short ok = AtLeast(Capacity, i, ask) && Nearby(Loc, i, L, D);
            if (ok) {
                berths[i][0] = ask;
                berths[i][1] = berths[i][1];
            } else {
                berths[i][0] = berths[i][0];
                berths[i][1] = ask;
            }
            if (is_solution(berths, N)) {
                break;
            }
            i = i + 1;
        }
    }

    short result[16] = {berths[0][0], berths[0][1],
    berths[1][0], berths[1][1],
    berths[2][0], berths[2][1],
    berths[3][0], berths[3][1],
    berths[4][0], berths[4][1],
    berths[5][0], berths[5][1],
    berths[6][0], berths[6][1],
    berths[7][0], berths[7][1]};
    __CPROVER_assert(0, "ret-val assertion");
}
