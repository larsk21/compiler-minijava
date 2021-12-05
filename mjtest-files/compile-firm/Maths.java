class Math {
    public static void main(String[] args) {
        int division1 = 200 / 10;
        int division2 = division1 / 4;
        int division3 = division1 / division2;
        System.out.println(division3);

        division1 = 200 / 10;
        division2 = division1 / 1;
        division3 = division1 / division2;
        System.out.println(division3);

        int a = 5;
        int b = 3;
        int c = a % b + 1;
        System.out.println(c);
    }
}