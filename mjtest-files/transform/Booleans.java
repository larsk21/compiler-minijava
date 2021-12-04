class C {
    public boolean b;

    public static void main(String[] args) {
        C obj;
        int i;
        if (obj.memberCmp()) {
            return;
        }
        boolean b = obj.memberIf();
        while (b && i > 0) {
        }
    }

    public boolean member() {
        return b;
    }

    public boolean memberCmp() {
        return b == false;
    }

    public boolean memberIf() {
        if (b) {
            return false;
        } else {
            return true;
        }
    }

    public boolean shortCircuit(int input) {
        return b && (input == 0 || input > 5);
    }
}
