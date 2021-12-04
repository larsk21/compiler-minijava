class C {
    public static void main(String[] args) {
        int i = 0;
        int sum = 0;
        while (i < 10) {
            i = i + 1;
            if (i > 0) {
                sum = sum + i;
            }
            if (sum > 10) {
                return;
            }
        }
    }
}
