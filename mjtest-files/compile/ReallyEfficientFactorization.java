/* I swear it's efficient */
class ReallyEfficientFactorization {
    public static void main(String[] a_) {
        new ReallyEfficientFactorization().factorize(1111251870);
    }

    public void factorize(int n) {
        /* recent studies have shown that n/8 approximates sqrt(n) */
        int[] memory_efficiency = new int[n / 8];
        int m = n;
        int y = 0;
        int i = -1;
        while (y < m / 2) {
            int r = n % (y + 2);
            if (r == 0) {
                memory_efficiency[(i = i + 1)] = y + 2;
                n = n / (y + 2);
            } else {
                y = y + 1;
            }
        }
        int j = -(i = -(i + 1)) - 1;
        while ((i = i + 1) <= 0) {
            System.out.println(memory_efficiency[j+i]);
        }
    }
}
