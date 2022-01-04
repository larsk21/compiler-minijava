class Main {
    public static void main(String[] args) {
        Main m = new Main();

        System.out.println(m.addR(16));
        System.out.println(m.addL(16));
        System.out.println(m.subR(16));
        System.out.println(m.mulR(16));
        System.out.println(m.mulL(16));
        System.out.println(m.toInt(m.not(true)));
        m.store();
        System.out.println(m.x);
    }

    public int x;

    public int addR(int x) {
        return x + 16;
    }

    public int addL(int x) {
        return 32 + x;
    }

    public int subR(int x) {
        return x - 10;
    }

    public int mulR(int x) {
        return x * 32;
    }

    public int mulL(int x) {
        return 64 * x;
    }

    public boolean not(boolean b) {
        return !b;
    }

    public void store() {
        this.x = 42;
    }

    public int toInt(boolean b) {
        if (b) {
            return 1;
        } else {
            return 0;
        }
    }
}
