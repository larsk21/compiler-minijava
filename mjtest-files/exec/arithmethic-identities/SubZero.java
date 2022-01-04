class AddZero {
    public static void main(String[] args) {
        AddZero a = new AddZero();

        System.out.println(a.pre_simple(44));
        System.out.println(a.pre_simple(-54));
        System.out.println(a.post_simple(66));
        System.out.println(a.post_simple(-66));

        System.out.println(a.pre_conditional(44));
        System.out.println(a.pre_conditional(-54));
        System.out.println(a.post_conditional(66));
        System.out.println(a.post_conditional(-66));
    }

    public int pre_simple(int x) {
        return 0 - x;
    }

    public int post_simple(int x) {
        return x - 0;
    }

    public int pre_conditional(int x) {
        if (0 == 1) {
            return 0 - x;
        } else {
            return x;
        }
    }

    public int post_conditional(int x) {
        if (0 == 1) {
            return x - 0;
        } else {
            return x;
        }
    }
}
