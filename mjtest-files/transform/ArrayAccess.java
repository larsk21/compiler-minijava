class c {
    public static void main(String[] args) {
        c obj = new c();
        System.out.println(obj.m());
    }

    public int m() {
        int[] array = new int[2];
        return array[0];
    }
}
