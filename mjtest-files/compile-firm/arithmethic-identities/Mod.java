class Mod {
    public static void main(String[] args) {
        Mod m = new Mod();

        System.out.println(m.modOne(-42));
        System.out.println(m.modOne(1234));

        System.out.println(m.modNegOne(13));
        System.out.println(m.modNegOne(-1024));
    }

    public int modOne(int x) {
        return x % 1;
    }

    public int modNegOne(int x) {
        return x % -1;
    }
}
