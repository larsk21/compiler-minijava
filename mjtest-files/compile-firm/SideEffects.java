class SideEffects {
    public static void main(String[] a_) {
        SideEffects s = new SideEffects();
        int[] arr = new int[2];
        arr[s.sideEffect(0, 0) + s.sideEffect(arr[0], 1)] =
                (arr[s.sideEffect(arr[0], 2)] = s.sideEffect(
                        s.sideEffect(1, 3),
                        s.sideEffect(5, 4)
                ));
        System.out.println(arr[0]);
        System.out.println(arr[1]);
    }

    public int sideEffect(int val, int out) {
        System.out.println(out);
        return val;
    }
}