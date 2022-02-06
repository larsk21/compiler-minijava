class Test {
    public static void main(String[] args) {
        int i = 0;
        while (i < 10) {
            int j = 0;
            while (j < 999 && j < 1000) {
                j = j + 1;
            }

            i = i + 1;
        }
        System.out.println(i);
    }
}
