class MulZero {
    public static void main(String[] args) {
        MulZero m = new MulZero();

        System.out.println(m.simple(42));
        System.out.println(m.simple(-1337));

        System.out.println(m.callNoSideEffects(42));
        System.out.println(m.callNoSideEffects(-1337));

        Data d = new Data();
        System.out.println(d.withSideEffects(128));
        System.out.println(d.z);

        d = new Data();
        System.out.println(d.callWithSideEffects(1337));
        System.out.println(d.z);
    }

    public int simple(int x) {
        return 0 * x;
    }

    public int callNoSideEffects(int x) {
        return f(x) * 0;
    }

    public int f(int x) {
        return x;
    }
}

class Data {
    public int z;

    public int withSideEffects(int x) {
        return 0 * (this.z = x);
    }

    public int callWithSideEffects(int x) {
        return f(x) * 0;
    }

    public int f(int x) {
        return this.z = x;
    }
}
