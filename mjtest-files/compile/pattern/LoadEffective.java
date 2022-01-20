class Main {
    public static void main(String[] args) {
        Main m = new Main();

        System.out.println(m.baseOffset(0));
        System.out.println(m.baseOffset(128));
        System.out.println(m.baseOffset(-1234));

        System.out.println(m.baseIndex(0, 42));
        System.out.println(m.baseIndex(128, 28));
        System.out.println(m.baseIndex(-1234, 43));

        System.out.println(m.baseIndexOffset(0, 42));
        System.out.println(m.baseIndexOffset(128, 28));
        System.out.println(m.baseIndexOffset(-1234, 43));

        System.out.println(m.index2(0));
        System.out.println(m.index2(128));
        System.out.println(m.index2(-1234));

        System.out.println(m.index4(0));
        System.out.println(m.index4(128));
        System.out.println(m.index4(-1234));

        System.out.println(m.index8(0));
        System.out.println(m.index8(128));
        System.out.println(m.index8(-1234));

        System.out.println(m.baseIndexScale(0, 42));
        System.out.println(m.baseIndexScale(128, 28));
        System.out.println(m.baseIndexScale(-1234, 43));

        System.out.println(m.baseIndexScaleOffset(0, 42));
        System.out.println(m.baseIndexScaleOffset(128, 28));
        System.out.println(m.baseIndexScaleOffset(-1234, 43));

        System.out.println(m.indexScaleOffset(0));
        System.out.println(m.indexScaleOffset(128));
        System.out.println(m.indexScaleOffset(-1234));


    }

    public int baseOffset(int x) {
        return x + 1337;
    }

    public int baseIndex(int x, int y) {
        return x + y;
    }

    public int baseIndexOffset(int x, int y) {
        return x + y + 1337;
    }

    public int index2(int x) {
        return x * 2;
    }

    public int index4(int x) {
        return x * 4;
    }

    public int index8(int x) {
        return x * 8;
    }

    public int baseIndexScale(int x, int y) {
        return x + y * 8;
    }

    public int baseIndexScaleOffset(int x, int y) {
        return -42 + x + y * 8;
    }

    public int indexScaleOffset(int x) {
        return -42 + x * 8;
    }
}
