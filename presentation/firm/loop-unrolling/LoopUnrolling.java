class Main {
    public int x;
    public int y;

    public static void main(String[] args) {
        System.out.println(new Main().foo());
    }

    public int foo() {
        int i = 0;
        while (i < 4) {
            i = i + 1;
        }
        return i;
    }
}
