class Main {
    public static void main(String[] args) {
        Main m = new Main();

        System.out.println(m.mulP2(8));
        System.out.println(m.mulP2(-42));
        System.out.println(m.mulNegP2(8));
        System.out.println(m.mulNegP2(-42));

        m.testMul(0);
        m.testMul(1);
        m.testMul(-1);
        m.testMul(1024);
        m.testMul(-1337);
        m.testMul(-2147483648);
        m.testMul(2147483647);
    }

    public int mulP2(int x) {
        return x * 1024;
    }

    public int mulNegP2(int x) {
        return x * -1024;
    }

    public void testMul(int x) {
        System.out.println(x * 2);
        System.out.println(x * 4);
        System.out.println(x * 8);
        System.out.println(x * 16);
        System.out.println(x * 32);
        System.out.println(x * 64);
        System.out.println(x * 128);
        System.out.println(x * 256);
        System.out.println(x * 512);
        System.out.println(x * 1024);
        System.out.println(x * 2048);
        System.out.println(x * 4096);
        System.out.println(x * 8192);
        System.out.println(x * 16384);
        System.out.println(x * 32768);
        System.out.println(x * 65536);
        System.out.println(x * 131072);
        System.out.println(x * 262144);
        System.out.println(x * 524288);
        System.out.println(x * 1048576);
        System.out.println(x * 2097152);
        System.out.println(x * 4194304);
        System.out.println(x * 8388608);
        System.out.println(x * 16777216);
        System.out.println(x * 33554432);
        System.out.println(x * 67108864);
        System.out.println(x * 134217728);
        System.out.println(x * 268435456);
        System.out.println(x * 536870912);
        System.out.println(x * 1073741824);

        System.out.println(x * -2);
        System.out.println(x * -4);
        System.out.println(x * -8);
        System.out.println(x * -16);
        System.out.println(x * -32);
        System.out.println(x * -64);
        System.out.println(x * -128);
        System.out.println(x * -256);
        System.out.println(x * -512);
        System.out.println(x * -1024);
        System.out.println(x * -2048);
        System.out.println(x * -4096);
        System.out.println(x * -8192);
        System.out.println(x * -16384);
        System.out.println(x * -32768);
        System.out.println(x * -65536);
        System.out.println(x * -131072);
        System.out.println(x * -262144);
        System.out.println(x * -524288);
        System.out.println(x * -1048576);
        System.out.println(x * -2097152);
        System.out.println(x * -4194304);
        System.out.println(x * -8388608);
        System.out.println(x * -16777216);
        System.out.println(x * -33554432);
        System.out.println(x * -67108864);
        System.out.println(x * -134217728);
        System.out.println(x * -268435456);
        System.out.println(x * -536870912);
        System.out.println(x * -1073741824);
        System.out.println(x * -2147483648);

        System.out.println(2 * x);
        System.out.println(4 * x);
        System.out.println(8 * x);
        System.out.println(16 * x);
        System.out.println(32 * x);
        System.out.println(64 * x);
        System.out.println(128 * x);
        System.out.println(256 * x);
        System.out.println(512 * x);
        System.out.println(1024 * x);
        System.out.println(2048 * x);
        System.out.println(4096 * x);
        System.out.println(8192 * x);
        System.out.println(16384 * x);
        System.out.println(32768 * x);
        System.out.println(65536 * x);
        System.out.println(131072 * x);
        System.out.println(262144 * x);
        System.out.println(524288 * x);
        System.out.println(1048576 * x);
        System.out.println(2097152 * x);
        System.out.println(4194304 * x);
        System.out.println(8388608 * x);
        System.out.println(16777216 * x);
        System.out.println(33554432 * x);
        System.out.println(67108864 * x);
        System.out.println(134217728 * x);
        System.out.println(268435456 * x);
        System.out.println(536870912 * x);
        System.out.println(1073741824 * x);

        System.out.println(-2 * x);
        System.out.println(-4 * x);
        System.out.println(-8 * x);
        System.out.println(-16 * x);
        System.out.println(-32 * x);
        System.out.println(-64 * x);
        System.out.println(-128 * x);
        System.out.println(-256 * x);
        System.out.println(-512 * x);
        System.out.println(-1024 * x);
        System.out.println(-2048 * x);
        System.out.println(-4096 * x);
        System.out.println(-8192 * x);
        System.out.println(-16384 * x);
        System.out.println(-32768 * x);
        System.out.println(-65536 * x);
        System.out.println(-131072 * x);
        System.out.println(-262144 * x);
        System.out.println(-524288 * x);
        System.out.println(-1048576 * x);
        System.out.println(-2097152 * x);
        System.out.println(-4194304 * x);
        System.out.println(-8388608 * x);
        System.out.println(-16777216 * x);
        System.out.println(-33554432 * x);
        System.out.println(-67108864 * x);
        System.out.println(-134217728 * x);
        System.out.println(-268435456 * x);
        System.out.println(-536870912 * x);
        System.out.println(-1073741824 * x);
        System.out.println(-2147483648 * x);
    }
}
