class Test {
    public static void main(String[] args) {
        System.out.println(new Test().hindex());
        System.out.println(new Test().minimal());
        System.out.println(new Test().unconditional());
    }

    public int hindex() {
        int[] help = new int[2];
        help[0] = 0;
        help[1] = 0;

        int i = 0;
        while (i <= 1) {
            if (i > help[i])
                return i-1;
            i = i + 1;
        }
        return 999;
    }

    public int minimal() {
        int i = 0;
        while (i < 10) {
            if (i == 5) {
                return 25;
            }
            i = i + 1;
        }
        return i;
    }

    public int unconditional() {
        int i = 0;
        while (i < 10) {
            return 42;
        }
        return i;
    }
}
