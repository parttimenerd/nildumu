int main(){
    int h = INPUT(int);
    int z = 0;
    while (z != h) {
        z = z + 1;
    }
    LEAK(z);
}