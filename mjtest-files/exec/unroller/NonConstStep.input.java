class NonConstStep {
    public static void main(String[] args) {
        int i = 0;
        while (i < 1000) {
            i = i + System.in.read();;
        }
        System.out.println(i);
    }
}
