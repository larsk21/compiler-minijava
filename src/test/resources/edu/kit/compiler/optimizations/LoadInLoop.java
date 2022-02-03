class A {
    public int method(B b) {
        int s = b.a;
        doNothing(b.a);
        int j = 0;
        while (j < 50) {
            int k = b.a;
            j = j + k;
        }
        return b.a + s + j;
    }

    public void doNothing(int k) {
        return;
    }
}

class B {
    public int a;
}

class Main {
    public static void main(String[] args) {
        B b = new B();
        b.a = 2;
        A a = new A();
        a.method(b);
    }
}
