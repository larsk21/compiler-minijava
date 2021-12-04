class A {
    public int x;
    public B b;

    public static void main(String[] args) {
        A instance = new A();
        instance.b = new B();
        while (instance.call(instance.b) > 0) {
            if (instance.x == 0) {
                System.out.println(0);
            }
            instance.doSomething();
        }
    }

    public int call(B val) {
        return x + val.val();
    }

    public void doSomething() {
        this.x = x - 1;
    }
}

class B {
    public int val() {
        return 1;
    }
}
