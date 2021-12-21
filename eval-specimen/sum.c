/* Toy program from paper of Backes et. al: "Automatic */
/* discovery and quantification of information leaks" */
/* Should leak n */

int main() {
    LEAK(INPUT(int) + INPUT(int) + INPUT(int));
}