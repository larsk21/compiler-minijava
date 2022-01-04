class Sub {
    public static void main(String[] args) {
        Sub s = new Sub();

        System.out.println(s.subMinus(10, 5));
        System.out.println(s.subMinus(10, -10));

        System.out.println(s.MinusPlusLeft(100, 50));
        System.out.println(s.MinusPlusLeft(-42, 42));

        System.out.println(s.MinusPlusRight(32, -18));
        System.out.println(s.MinusPlusRight(1337, 337));

        System.out.println(s.MaxInt(0));

        System.out.println(s.MinInt(0));
    }

    public int subMinus(int x, int y) {
        return x - (-y);
    }

    public int MinusPlusLeft(int x, int y) {
        return (-x) + y;
    }

    public int MinusPlusRight(int x, int y) {
        return x + (-y);
    }

    public int MaxInt(int x) {
        return x - 2147483647;
    }

    public int MinInt(int x) {
        return x - (-2147483648);
    }
}
