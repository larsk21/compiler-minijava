class Main {
    public int x;
    public int y;

    public static void main(String[] args) {
        Main m = new Main();
        m.x = 42;
        m.y = 28;

        System.out.println(m.noOffset());
        System.out.println(m.offset());

        boolean[] bArr = new boolean[10];
        bArr[8] = true;
        System.out.println(m.toInt(m.index(bArr, 8)));

        int[] iArr = new int[128];
        iArr[99] = 1024;
        System.out.println(m.indexScale4(iArr, 99));

        Main[] mArr = new Main[16];
        mArr[4] = new Main();
        mArr[4].x = 8;
        System.out.println(m.indexScale8(mArr, 4));
    }

    public int noOffset() {
        return this.x;
    }

    public int offset() {
        return this.y;
    }

    public boolean index(boolean[] arr, int i) {
        return arr[i];
    }

    public int indexScale4(int[] arr, int i) {
        return arr[i];
    }

    public int indexScale8(Main[] arr, int i) {
        return arr[i].x;
    }


    public int toInt(boolean b) {
        if (b) {
            return 1;
        } else {
            return 0;
        }
    }
}
