class A {
    public int b;
    public int a() {
        return b = 4;
    }
}

class B {
    public static void main(String[] args) {
        A a = new A();
        System.out.println(a.a());
    }
}