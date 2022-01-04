class Minus {
    public static void main(String[] args) {
        Minus m = new Minus();

        System.out.println(m.twoMinus(42));
        System.out.println(m.twoMinus(0));
        System.out.println(m.twoMinus(-1337));

        System.out.println(m.threeMinus(42));
        System.out.println(m.threeMinus(0));
        System.out.println(m.threeMinus(-1337));

        System.out.println(m.fourMinus(42));
        System.out.println(m.fourMinus(0));
        System.out.println(m.fourMinus(-1337));

        System.out.println(m.manyMinus(42));
        System.out.println(m.manyMinus(0));
        System.out.println(m.manyMinus(-1337));
    }

    public int twoMinus(int x) {
        return -(-x);
    }

    public int threeMinus(int x) {
        return -(-(-x));
    }

    public int fourMinus(int x) {
        return -(-(-(-x)));
    }

    public int manyMinus(int x) {
        return -(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-(-x))))))))))))))))))))))))))))))))))))))))));
    }
}
