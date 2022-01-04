class Main {
    public static void main(String[] args) {
        Main m = new Main();
        System.out.println(m.returnConst());

        System.out.println(m.constPhi(true));
        System.out.println(m.constPhi(false));

        System.out.println(m.constSub(5));
        System.out.println(m.constSub(10));

        System.out.println(m.constSubCond(true, 64));
        System.out.println(m.constSubCond(false, 128));

        System.out.println(m.condConst(-1235));
        System.out.println(m.condConst(0));
        System.out.println(m.condConst(9));
        System.out.println(m.condConst(2048));
        System.out.println(m.condConst(256));

        System.out.println(m.constCall(new Data(), true));
        System.out.println(m.constCall(new Data(), false));

        System.out.println(m.constDivision(42, true));
        System.out.println(m.constDivision(-48, true));
        System.out.println(m.constDivision(15, false));
        System.out.println(m.constDivision(16, false));
    }

    public int returnConst() {
        return 42;
    }

    public int constPhi(boolean b) {
        int res;
        if (b) {
            res = 28;
        } else {
            res = 42;
        }
        return res;
    }

    public int constSub(int x) {
        return 5 - x;
    }

    public int constSubCond(boolean b, int x) {
        if (b) {
            return 32 - x;
        } else {
            return -x;
        }
    }

    public int condConst(int x) {
        if (x < 5) {
            if (x < -1234) {
                return 1;
            } else {
                return 2;
            }
        } else if (x > 10) {
            if (1024 > x) {
                return 3;
            } else {
                return 4;
            }
        } else {
            return 5;
        }
    }

    public int constCall(Data d, boolean b) {
        if (b) {
            return d.foo(2, 4, 8, 16, 32, 64);
        } else {
            return d.foo(64, 32, 16, 8, 4, 2);
        }
    }

    public int constDivision(int x, boolean b) {
        if (b) {
            return x / 42;
        } else {
            return x % 2;
        }
    }
}

class Data {
    public int foo(int x, int y, int z, int p, int q, int r) {
        return x + y * z + p / q - r;
    }
}


