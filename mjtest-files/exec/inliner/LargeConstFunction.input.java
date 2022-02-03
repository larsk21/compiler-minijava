/* a test case to force inlining of functions containing NoMem */
class Main {
    public static void main(String[] args) {
        Main m = new Main();
        /* the calls to foo are inlined */
        System.out.println(m.foo(System.in.read(), System.in.read()));
    }

    public int foo(int x, int y) {
        /* the calls to largeFn are too big to be inlined */
        /* largeFn is Const, so memory predecessors are replace with NoMem */
        return largeFn(x) + largeFn(y);
    }

    public int largeFn(int x) {
        /* the divisions are replaced with shift */
        /* the loops are unrolled completely */
        int i = 0;
        while (i < 64) {
            i = i + 1;
            x = x / 3;
        }

        i = 0;
        while (i < 64) {
            i = i + 1;
            x = x / 3;
        }

        return x;
    }
}
