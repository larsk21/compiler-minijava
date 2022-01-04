class MinusNormalization {
    public static void main(String[] args) {
        MinusNormalization m = new MinusNormalization();

        System.out.println(m.minusSub(42, 28));
        System.out.println(m.minusSub(-1337, 128));

        System.out.println(m.minusAddConst(95));
        System.out.println(m.minusAddConst(-105));

        System.out.println(m.minusMulConst(-42));
        System.out.println(m.minusMulConst(128));

        System.out.println(m.moreComplex(21, 21));
        System.out.println(m.moreComplex(-1024, 2048));
    }

    public int minusSub(int x, int y) {
        return -(x - y);
    }

    public int minusAddConst(int x) {
        return - (x + 5);
    }

    public int minusMulConst(int x) {
        return - (x * 10);
    }

    public int moreComplex(int x, int y) {
        return -(-x - y);  /* -->  x + y */
    }
}
