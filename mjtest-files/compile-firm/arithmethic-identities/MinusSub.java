class MinusSub {
    public static void main(String[] args) {
        MinusSub m = new MinusSub();

        System.out.println(m.simple(42, 28));

        System.out.println(m.moreComplex(21, 21));
        System.out.println(m.moreComplex(-1024, 2048));
    }

    public int simple(int x, int y) {
        return -(x - y);
    }

    public int moreComplex(int x, int y) {
        return -(-x - y);  /* -->  x + y */
    }
}
