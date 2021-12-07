class MinusSub {
    public static void main(String[] args) {
        MinusSub m = new MinusSub();

        System.out.println(m.simple(42, 28));
    }

    public int simple(int x, int y) {
        return -(x - y);
    }
}
