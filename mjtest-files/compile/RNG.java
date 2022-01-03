class Main {
    public static void main(String[] args) {
        Main m = new Main();
        int[] sequence = m.randomSequence(1, 100);
        int i = 0;
        while (i < 100) {
            System.out.println(sequence[i]);
            i = i + 1;
        }
    }

    public int[] randomSequence(int seed, int len) {
        int _18Bit = 256 * 256 * 4;

        RNG rng = new RNG();
        rng.init(seed);
        int[] result = new int[len];
        int i = 0;
        while (i < len) {
            int num = rng.getNext() / _18Bit;
            if (num < 0) {
                num = -num;
            }
            result[i] = num;
            i = i + 1;
        }
        return result;
    }
}

class RNG {
    public int currentVal;

    public void init(int seed) {
        currentVal = next(seed);
    }

    public int get() {
        return currentVal;
    }

    public int getNext() {
        currentVal = next(currentVal);
        return currentVal;
    }

    /* private */ public int next(int val) {
        int _primeFactor = 57203;
        int _16Bit = 256 * 256;

        val = val * _primeFactor;
        int upper = val * _16Bit;
        int lower = val / _16Bit;
        lower = lower * 31;
        return upper + lower + 7;
    }
}
