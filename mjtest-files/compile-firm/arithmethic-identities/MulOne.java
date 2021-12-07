class MulOne {
    public static void main(String[] args) {
        MulOne m = new MulOne();
        System.out.println(m.pre(-1));
        System.out.println(m.pre(42));
        System.out.println(m.post(0));
        System.out.println(m.post(-99));
        System.out.println(m.many(4));
        System.out.println(m.many(-33));
    }

    public int pre(int x) {
        return 1 * x;
    }

    public int post(int x) {
        return x * 1;
    }

    public int many(int x) {
        return 1 * 1 * 1 * 1 * x * 1 * 1 * 1 * 1 * 1 * 1;
    }
}
