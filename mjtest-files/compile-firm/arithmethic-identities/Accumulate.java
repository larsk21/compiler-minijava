class Accumulate {
    public static void main(String[] args) {
        Accumulate acc = new Accumulate();

        System.out.println(acc.add1(-12));
        System.out.println(acc.add1(128));

        System.out.println(acc.add1rev(-12));
        System.out.println(acc.add1rev(128));

        System.out.println(acc.add2(42));
        System.out.println(acc.add2(-28));

        System.out.println(acc.add2rev(1234));
        System.out.println(acc.add2rev(-4321));

        System.out.println(acc.sub1(-12));
        System.out.println(acc.sub1(128));

        System.out.println(acc.sub1rev(-12));
        System.out.println(acc.sub1rev(128));

        System.out.println(acc.sub2(42));
        System.out.println(acc.sub2(-28));

        System.out.println(acc.sub2rev(1234));
        System.out.println(acc.sub2rev(-4321));

        System.out.println(acc.mul(44));
        System.out.println(acc.mul(-12));

        System.out.println(acc.complex(1337));
        System.out.println(acc.complex(-6077));

        System.out.println(acc.complexMul(22));
        System.out.println(acc.complexMul(-5));
    }

    public int add1(int x) {
        return (x + 4) + 5;
    }

    public int add1rev(int x) {
        return 5 + (4 + x);
    }

    public int add2(int x) {
        return (4 - x) + 5;
    }

    public int add2rev(int x) {
        return 5 + (x - 4);
    }

    public int sub1(int x) {
        return 5 - (x + 4);
    }

    public int sub1rev(int x) {
        return (x + 12) - 8;
    }

    public int sub2(int x) {
        return 13 - (8 - x);
    }

    public int sub2rev(int x) {
        return (x - 99) - 33;
    }

    public int mul(int x) {
        return (x * 5) * 8;
    }

    public int mulRev(int x) {
        return 4 * (6 * x);
    }

    public int complex(int x) {
        return 5 - (5 + (4 - x)) + 3;
    }

    public int complexMul(int x) {
        return 4 * (-2 * (x * 3));
    }
}
