The evaluation code expects that there is a folder in the same parent
folder as the `nildumu` with the name `eval-programs`, that contains
the analysis programs as executables.

The `specimen` folder contains the tests.
Some of these tests are version of tests from papers or other sources.
The original sources a given in the header comment for each test case.

- test cases starting with `kq` originate from a paper by Klebanov
- … `ms` from a paper by Meng and Smith
- `kq` and `ms` test cases are obtained from
    the https://github.com/approxflow/approxflow repository


- expects the existence of a bash command (or alias) that brings
  the Java8 binaries into PATH, e.g
  `alias use_java8="export PATH=/usr/lib/jvm/java-1.8.0-openjdk-amd64/bin:$PATH"`