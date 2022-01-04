class AddSubZero {
    public static void main(String[] args) {
        AddSubZero a = new AddSubZero();

        System.out.println(a.test(1234, 4321));
        System.out.println(a.test(-28, 38));

        System.out.println(a.test2(42));
        System.out.println(a.test2(-99));
    }

    public int test(int x, int y) {
        /*  == x + y; */
        return 0 - (0 + (0 - x) + 0) - (0 - y) + 0 - 0 + 0 + 0 + 0;
    }

    public int test2(int x) {
        /* == 2 * x  */
        return 0 + 0 + 0 + 0 + x - 0 + x - 0 - 0 - 0;
    }
}
