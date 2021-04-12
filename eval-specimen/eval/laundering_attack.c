int nondet();

int main(){
    int h = nondet();
    int z = 0;
    while (z != h) {
        z = z + 1;
    }
    return z;
}