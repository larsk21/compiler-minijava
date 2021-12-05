class Main {
    public static void main(String[] args) {
        ArrayList list = new ArrayList().init();

        int n = 1000;

        int i = 0;
        while (i < n) {
            list.add(i);
            i = i + 1;
        }

        int j = 0;
        while (j < n) {
            System.out.println(list.get(j));
            j = j + 1;
        }
    }
}

class ArrayList {
    public int[] buffer;
    public int size;
    public int needle;

    public ArrayList init() {
        buffer = new int[4];
        size = 4;
        needle = 0;
        return this;
    }

    public void add(int x) {
        if (needle == size) {
            int[] old = buffer;
            buffer = new int[size * 2];

            int i = 0;
            while (i < size) {
                buffer[i] = old[i];
                i = i + 1;
            }
            size = size * 2;
        }

        buffer[needle] = x;
        needle = needle + 1;
    }

    public int get(int i) {
        return buffer[i];
    }
}
