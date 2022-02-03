class Main {
    public static void main(String[] args) {
        Main m = new Main();
        System.out.println(m.constantStep());
        System.out.println(m.conflictingStep());
        System.out.println(m.multiplePhis());
    }

    public int constantStep() {
        int i = 0;
        while (i < 10) {
            if (i < 5) { i = i + 1; } else { i = i + 1; }
        }
        return i;
    }

    public int conflictingStep() {
        int i = 0;
        while (i < 10) {
            if (i < 5) { i = i + 2; } else { i = i + 1; }
        }
        return i;
    }

    public int multiplePhis() {
        int i = 0;
        while (i < 10) {
            if (i < 5) { i = i + 1; } else { i = i + 1; }
            if (i > 5) { i = i + 1; } else { i = i + 1; }
        }
        return i;
    }
}
