class QuickSort {

    public void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    public int partition(int[] arr, int low, int high) {
        int pivot = arr[high];
        int i = (low - 1);

        int j = low;
        while (j <= high - 1) {
            if (arr[j] < pivot) {
                i = i + 1;
                swap(arr, i, j);
            }
            j = j + 1;
        }
        swap(arr, i + 1, high);
        return (i + 1);
    }

    public void quickSort(int[] arr, int low, int high) {
        if (low < high) {
            int pi = partition(arr, low, high);

            quickSort(arr, low, pi - 1);
            quickSort(arr, pi + 1, high);
        }
    }

    public void printArray(int[] arr, int size) {
        int i = 0;
        while (i < size) {
            System.out.println(arr[i]);
            i = i + 1;
        }
        System.out.println(-1);
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


class B {
    public static void main(String[] args) {
        QuickSort q = new QuickSort();

        B m = new B();
        int size = 50;
        int[] sequence = m.randomSequence(1, size);

        q.printArray(sequence, size);

        q.quickSort(sequence, 0, size - 1);
        q.printArray(sequence, size);
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
