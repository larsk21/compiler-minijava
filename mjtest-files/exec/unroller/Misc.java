class Main {
    public static void main(String[] args) {
        Main m = new Main();

        System.out.println(m.manyIterations());
        System.out.println(m.noIterations());
        System.out.println(m.maxIterations());
        System.out.println(m.sumTo100());
        System.out.println(m.sumTo1024());
    }

    public int manyIterations() {
        int i = -2147483648;
        while (i < 0) {
            i = i + 1;
        }
        return i;
    }

    public int noIterations() {
        int i = -2147483648;
        while (i < -2147483648) {
            i = i + 1;
        }
        return i;
    }

    /* This loop can not actually be unrolled */
    public int maxIterations() {
        int i = -2147483648;
        while (i < 2147483647) {
            i = i + 1;
        }
        return i;
    }

    public int sumTo100() {
        int i = 1;
        int sum = 0;
        while (i <= 100) {
            sum = sum + i;
            i = i + 1;
        }
        return sum;
    }

    public int sumTo1024() {
        int i = 1;
        int sum = 0;
        while (i <= 1024) {
            sum = sum + i;
            i = i + 1;
        }
        return sum;
    }
}
