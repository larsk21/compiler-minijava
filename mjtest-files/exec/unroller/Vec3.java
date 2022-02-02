class Main {
    public static void main(String[] args) {
        RNG rng = new RNG().init(1337);

        Main m = new Main();
        m.foo();
        m.bar(rng);
        m.baz(rng);
    }

    public void foo() {
        new Vec3().initAll(42)
            .mul(new Vec3().initNormal(1))
            .add(new Vec3().initOne())
            .print();
    }

    public void bar(RNG rng) {
        new Vec3().initRandom(rng)
            .abs()
            .div(new Vec3().initRandom(rng).abs().neg())
            .mod(new Vec3().initWith(28, 42, 99))
            .add(new Vec3().initRandom(rng).mul(new Vec3().initZero()))
            .print();
    }

    public void baz(RNG rng) {
        System.out.println(
            new Vec3().initRandom(rng)
                .sub(new Vec3().initRandom(rng))
                .dot(new Vec3().initRandom(rng))
        );
    }

    public void bla(RNG rng) {
        new Vec3().initZero()
            .add(new Vec3().initOne())
            .add(new Vec3().initOne())
            .div(new Vec3().initOne())
            .mul(new Vec3().initOne())
            .sub(new Vec3().initOne())
            .sub(new Vec3().initOne())
            .add(new Vec3().initOne())
            .add(new Vec3().initOne())
            .sub(new Vec3().initOne())
            .add(new Vec3().initOne())
            .sub(new Vec3().initOne())
            .add(new Vec3().initOne())
            .print();
    }
}


class Vec3 {
    public int[] arr;

    public Vec3 init() {
        this.arr = new int[3];
        return this;
    }

    public Vec3 initWith(int x, int y, int z) {
        init();
        arr[0] = x;
        arr[1] = y;
        arr[2] = z;
        return this;
    }

    public Vec3 initAll(int x) {
        init();
        int i = 0;
        while (i < 3) {
            arr[i] = x;
            i = i + 1;
        }
        return this;
    }

    public Vec3 initZero() {
        init();
        int i = 0;
        while (i < 3) {
            arr[i] = 0;
            i = i + 1;
        }
        return this;
    }

    public Vec3 initOne() {
        init();
        int i = 0;
        while (i < 3) {
            arr[i] = 1;
            i = i + 1;
        }
        return this;
    }

    public Vec3 initRandom(RNG rng) {
        init();
        int i = 0;
        while (i < 3) {
            arr[i] = rng.getNext();
            i = i + 1;
        }
        return this;
    }

    public Vec3 initNormal(int n) {
        initZero();
        arr[n] = 1;
        return this;
    }

    public Vec3 neg() {
        Vec3 result = new Vec3().init();

        int i = 0;
        while (i < 3) {
            result.arr[i] = -this.arr[i];
            i = i + 1;
        }
        return result;
    }

    public Vec3 abs() {
        Vec3 result = new Vec3().init();

        int i = 0;
        while (i < 3) {
            if (arr[i] < 0) {
                result.arr[i] = -arr[i];
            } else {
                result.arr[i] = arr[i];
            }
            i = i + 1;
        }
        return result;
    }

    public Vec3 add(Vec3 other) {
        Vec3 result = new Vec3().init();

        int i = 0;
        while (i < 3) {
            result.arr[i] = this.arr[i] + other.arr[i];
            i = i + 1;
        }
        return result;
    }

    public Vec3 sub(Vec3 other) {
        Vec3 result = new Vec3().init();

        int i = 0;
        while (i < 3) {
            result.arr[i] = this.arr[i] - other.arr[i];
            i = i + 1;
        }
        return result;
    }

    public Vec3 mul(Vec3 other) {
        Vec3 result = new Vec3().init();

        int i = 0;
        while (i < 3) {
            result.arr[i] = this.arr[i] * other.arr[i];
            i = i + 1;
        }
        return result;
    }

    public Vec3 div(Vec3 other) {
        Vec3 result = new Vec3().init();

        int i = 0;
        while (i < 3) {
            result.arr[i] = this.arr[i] / other.arr[i];
            i = i + 1;
        }
        return result;
    }

    public Vec3 mod(Vec3 other) {
        Vec3 result = new Vec3().init();

        int i = 0;
        while (i < 3) {
            result.arr[i] = this.arr[i] % other.arr[i];
            i = i + 1;
        }
        return result;
    }

    public int dot(Vec3 other) {
        int result = 0;

        int i = 0;
        while (i < 3) {
            result = result + this.arr[i] * other.arr[i];
            i = i + 1;
        }
        return result;
    }

    public void print() {
        int i = 0;
        while (i < 3) {
            System.out.println(arr[i]);
            i = i + 1;
        }
        System.out.write(10);
    }
}

class RNG {
    public int currentVal;

    public RNG init(int seed) {
        currentVal = next(seed);
        return this;
    }

    public int get() {
        return currentVal;
    }

    public int getNext() {
        currentVal = next(currentVal);
        return currentVal;
    }

    public int next(int val) {
        int _primeFactor = 57203;
        int _16Bit = 256 * 256;

        val = val * _primeFactor;
        int upper = val * _16Bit;
        int lower = val / _16Bit;
        lower = lower * 31;
        return upper + lower + 7;
    }
}

