"""
Calls the RC2 solver from pysat

Accepts as an argument a DIMACS file and prínts
```
	o [weight of the optimal solution]
	v [assignments of the optimal solution]
```
"""

from pysat.examples.rc2 import RC2
from pysat.formula import WCNF
import sys

with RC2(WCNF(from_file=sys.argv[1])) as rc2:
	m = rc2.compute()
	print(f"o {rc2.cost}")
	print("v " + " ".join(map(str, m)))
