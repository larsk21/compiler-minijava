class Main {
    public static void main(String[] args) {
        Main m = new Main();

        System.out.println(m.eqR(0));
        System.out.println(m.eqR(42));

        System.out.println(m.eqL(0));
        System.out.println(m.eqL(42));

        System.out.println(m.neqR(0));
        System.out.println(m.neqR(42));

        System.out.println(m.neqL(0));
        System.out.println(m.neqL(42));

        System.out.println(m.ltR(-8));
        System.out.println(m.ltR(0));
        System.out.println(m.ltR(8));

        System.out.println(m.ltL(-8));
        System.out.println(m.ltL(0));
        System.out.println(m.ltL(8));

        System.out.println(m.leR(-8));
        System.out.println(m.leR(0));
        System.out.println(m.leR(8));

        System.out.println(m.leL(-8));
        System.out.println(m.leL(0));
        System.out.println(m.leL(8));

        System.out.println(m.gtR(-8));
        System.out.println(m.gtR(0));
        System.out.println(m.gtR(8));

        System.out.println(m.gtL(-8));
        System.out.println(m.gtL(0));
        System.out.println(m.gtL(8));

        System.out.println(m.geR(-8));
        System.out.println(m.geR(0));
        System.out.println(m.geR(8));

        System.out.println(m.geL(-8));
        System.out.println(m.geL(0));
        System.out.println(m.geL(8));
    }

    public int eqR(int x) {
        if (x == 0) { return 1; } else { return 0; }
    }

    public int eqL(int x) {
        if (0 == x) { return 1; } else { return 0; }
    }

    public int neqR(int x) {
        if (x != 0) { return 1; } else { return 0; }
    }

    public int neqL(int x) {
        if (0 != x) { return 1; } else { return 0; }
    }

    public int ltR(int x) {
        if (x < 0) { return 1; } else { return 0; }
    }

    public int ltL(int x) {
        if (0 > x) { return 1; } else { return 0; }
    }

    public int leR(int x) {
        if (x <= 0) { return 1; } else { return 0; }
    }

    public int leL(int x) {
        if (0 >= x) { return 1; } else { return 0; }
    }

    public int gtR(int x) {
        if (x > 0) { return 1; } else { return 0; }
    }

    public int gtL(int x) {
        if (0 < x) { return 1; } else { return 0; }
    }

    public int geR(int x) {
        if (x >= 0) { return 1; } else { return 0; }
    }

    public int geL(int x) {
        if (0 <= x) { return 1; } else { return 0; }
    }
}
