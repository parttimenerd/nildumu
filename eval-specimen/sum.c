/* Toy program from paper of Backes et. al: "Automatic */
/* discovery and quantification of information leaks" */
/* Should leak n */
int nondet();
int nondet2();
int nondet3();
int main() {
    return nondet() + nondet2() + nondet3();
}