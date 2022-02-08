public int fibonacci42() {
    int n = 0; int a = 0; int b = 1;
    while (n < 42) {
        int c = a + b;
        a = b; b = c;
        n = n + 1;
    }
    return a;
}
