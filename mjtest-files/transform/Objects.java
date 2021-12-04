class A {
    public int x;
    public B b;

    public static void main(String[] args) {
        A instance;
        while (instance.call(instance.b) > 0) {
            instance.doSomething();
            if (instance.x == 0) {
                return;
            }
        }
    }

    public int call(B val) {
        return b.val() + val.val();
    }

    public void doSomething() { }
}

class B {
    public A a;

    public int val() {
        return a.x;
    }
}
