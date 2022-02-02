class Main {
    public static void main(String[] args) {
        Main m = new Main();

        System.out.println(m.testNonConstStep());
        System.out.println(m.testNonConstStep2());
        System.out.println(m.testNonConstLimit());
    }

    public int testNonConstStep() {
        int i = 0;
        while (i < 10) {
            if (i == 5) {
                i = i + 2;
            } else {
                i = i + 1;
            }
        }
        return i;
    }

    public int testNonConstStep2() {
        int i = 0;
        while (i < 10) {
            if (i == 0) {
                i = i + 2;
            } else if (i == 1) {
                i = i + 1;
            } else if (i == 2) {
                i = i + 1;
            } else if (i == 3) {
                i = i + 2;
            } else if (i == 4) {
                i = i + 1;
            } else if (i == 5) {
                i = i + 1;
            } else if (i == 6) {
                i = i + 1;
            } else if (i == 7) {
                i = i + 1;
            } else if (i == 8) {
                i = i + 1;
            } else if (i == 9) {
                i = i + 1;
            }
        }
        return i;
    }

    public int testNonConstLimit() {
        int limit = System.in.read();
        int sum = 0;
        int i = 1;
        while (i < limit) {
            sum = sum + i;
            i = i + 1;
        }
        return sum;
    }
}
