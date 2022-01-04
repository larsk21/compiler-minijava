class DivOne {
    public static void main(String[] args) {
        DivOne d = new DivOne();

        System.out.println(d.divOne(0));
        System.out.println(d.divOne(42));
        System.out.println(d.divOne(-2));

        System.out.println(d.divOneMultiple(0));
        System.out.println(d.divOneMultiple(42));
        System.out.println(d.divOneMultiple(-2));

        System.out.println(d.divNegOne(1337));
        System.out.println(d.divNegOne(-28));
        System.out.println(d.divNegOne(0));
    }

    public int divOne(int x) {
        return x / 1;
    }

    public int divOneMultiple(int x) {
        return x / 1 / 1 / 1 / 1;
    }

    public int divNegOne(int x) {
        return x / -1;
    }
}
