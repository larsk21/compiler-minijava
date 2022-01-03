/*
A basic fraction-based raytracer that renders arbitrary triangles from stdin into a 257x257 PPM [0].

Example input (a blue triangle above and in front of a red triangle):
TVF-1#1#F1#1#F2#1#VF1#1#F1#1#F2#1#VF0#1#F-1#1#F2#1#C255#0#0#
TVF-1#1#F2#1#F2#1#VF1#1#F2#1#F2#1#VF0#1#F0#1#F2#1#C0#0#255#

Save this into example.tri (including a trailing newline) and run:

./a.out < example.tri | convert ppm:- example.png

The camera origin is at 0,0,0 and the camera points toward 0,0,1.5.

[0]: https://de.wikipedia.org/wiki/Portable_Anymap
 */
class Raytracer {
    public static void main(String[] args) throws Exception {
        TriangleList triangles = new TriangleParser().parse();

        Renderer r = new Renderer().init(triangles);

        Vec3 cameraOrigin = new Vec3().initInts(0, 0, 0);
        Vec3 cameraVector = new Vec3().init(new Frac().initInt(0), new Frac().initInt(0), new Frac().init(3, 2));
        r.render(
                new Color().init_c(0, 0, 0),
                cameraOrigin,
                cameraVector,
                257, 257, new Frac().init(1, 128)
        );
        System.out.flush();
    }
}

/* A singly linked list to dynamically allocate space for triangles */
class TriangleList {
    public Triangle t;
    public TriangleList ts;

    public TriangleList init(Triangle t, TriangleList ts) {
        this.t = t;
        this.ts = ts;

        return this;
    }
}

/*
Parser the TRI (Triangle Rendering Input) format :P
Grammar (no extra characters allowed):

Triangles -> Triangle*
Triangle -> 'T' Vec3 Vec3 Vec3 Color '\n'
Vec3 -> 'V' Frac Frac Frac
Frac -> 'F' Int Int
Color -> 'C' Int Int Int
Int -> ('0' ... '9' '-')* '#'
 */
class TriangleParser {
    public TriangleList parse() throws Exception {
        TriangleList triangles = null;
        Triangle t;

        while ((t = parseTriangle()) != null) {
            triangles = new TriangleList().init(t, triangles);
        }

        return triangles;
    }

    public Triangle parseTriangle() throws Exception {
        /* character T */
        if (System.in.read() != 84) {
            return null;
        }

        Vec3 vertex0;
        Vec3 vertex1;
        Vec3 vertex2;
        Color color;

        if ((vertex0 = parseVec3()) == null || (vertex1 = parseVec3()) == null || (vertex2 = parseVec3()) == null || (color = parseColor()) == null) {
            return null;
        }

        if (System.in.read() != 10) {
            return null;
        }

        return new Triangle().init(vertex0, vertex1, vertex2, color);
    }

    public Vec3 parseVec3() throws Exception {
        /* character V */
        if (System.in.read() != 86) {
            return null;
        }

        Frac x;
        Frac y;
        Frac z;

        if ((x = parseFrac()) == null || (y = parseFrac()) == null || (z = parseFrac()) == null) {
            return null;
        }

        return new Vec3().init(x, y, z);
    }

    public Frac parseFrac() throws Exception {
        /* character F */
        if (System.in.read() != 70) {
            return null;
        }

        int numerator = parseInt();
        int denominator = parseInt();

        return new Frac().init(numerator, denominator);
    }

    public Color parseColor() throws Exception {
        /* character C */
        if (System.in.read() != 67) {
            return null;
        }

        int r = parseInt();
        int g = parseInt();
        int b = parseInt();

        return new Color().init_c(r, g, b);
    }

    public int parseInt() throws Exception {
        int lookahead = System.in.read();
        int i = 0;
        int sign = 1;

        /* ends with character # */
        while (lookahead != 35) {
            if (lookahead == 45) {
                sign = -1;
            }

            if (lookahead >= 48 && lookahead <= 57) {
                i = i * 10 + lookahead - 48;
            }

            lookahead = System.in.read();
        }

        return i * sign;
    }
}

class Renderer {
    public TriangleList triangles;

    public Vec3 rayOrigin;
    public Vec3 rayVector;
    public Vec3 rayOffset;

    public Frac zero;
    public Frac one;
    public Frac eps;
    public Frac negEps;
    public Frac big;

    public Vec3 edge1;
    public Vec3 edge2;
    public Vec3 pvec;
    public Frac idet;
    public Vec3 tvec;
    public Frac v;
    public Vec3 qvec;
    public Frac u;

    public Frac t_;
    public Frac t;

    public Renderer init(TriangleList triangles) {
        int p = 32767;

        this.triangles = triangles;

        this.rayOrigin = new Vec3().initInts(0, 0, 0);
        this.rayVector = new Vec3().initInts(0, 0, 0);
        this.rayOffset = new Vec3().initInts(0, 0, 0);

        this.zero = new Frac().initInt(0);
        this.one = new Frac().initInt(1);
        this.eps = new Frac().init(1, p);
        this.negEps = new Frac().init(-1, p);
        this.big = new Frac().init(p, 1);

        this.edge1 = new Vec3().initInts(0, 0, 0);
        this.edge2 = new Vec3().initInts(0, 0, 0);
        this.pvec = new Vec3().initInts(0, 0, 0);
        this.idet = new Frac().initInt(0);
        this.tvec = new Vec3().initInts(0, 0, 0);
        this.v = new Frac().initInt(0);
        this.qvec = new Vec3().initInts(0, 0, 0);
        this.u = new Frac().initInt(0);

        this.t_ = new Frac().initInt(0);
        this.t = new Frac().initInt(0);

        return this;
    }

    /*
    Moeller-Trumbore triangle intersection [0].

    [0]: https://en.wikipedia.org/wiki/M%C3%B6ller%E2%80%93Trumbore_intersection_algorithm
     */
    public boolean intersect(Triangle tr) {
        edge1.initVec(tr.vertex1);
        edge1.subtract(tr.vertex0);
        edge2.initVec(tr.vertex2);
        edge2.subtract(tr.vertex0);

        pvec.cross(rayVector, edge2);

        edge1.dot(pvec, idet);
        if (idet.lt(eps) && idet.gt(negEps)) {
            return false;
        }

        idet.invert();

        tvec.initVec(rayOrigin);
        tvec.subtract(tr.vertex0);
        tvec.dot(pvec, v);
        v.times(idet);

        if (v.lt(zero) || v.gt(one)) {
            return false;
        }

        qvec.cross(tvec, edge1);
        rayVector.dot(qvec, u);
        u.times(idet);

        if (u.lt(zero)) {
            return false;
        }
        u.add(v);
        if (u.gt(one)) {
            return false;
        }

        edge2.dot(qvec, t);
        t.times(idet);

        return true;
    }

    public void render(Color background, Vec3 cameraOrigin, Vec3 cameraVector, int w, int h, Frac pixDist) {
        System.out.write(80);
        System.out.write(51);
        System.out.write(10);

        System.out.println(w);
        System.out.println(h);

        System.out.println(255);

        int y = h - 1;
        while (y >= 0) {
            int x = 0;
            while (x < w) {
                rayOrigin.initVec(cameraOrigin);
                rayOrigin.add(cameraVector);
                rayOffset.initInts(x - (w / 2), y - (h / 2), 0);
                rayOffset.timesFrac(pixDist);
                rayOrigin.add(rayOffset);

                rayVector.initVec(rayOrigin);
                rayVector.subtract(cameraOrigin);

                TriangleList cursor = this.triangles;
                Triangle intersected = null;
                t_.initFrac(big);
                while (cursor != null) {
                    if (intersect(cursor.t) && t.gt(zero) && t.lt(t_)) {
                        intersected = cursor.t;
                        t_.initFrac(t);
                    }

                    cursor = cursor.ts;
                }

                if (intersected == null) {
                    background.print();
                } else {
                    intersected.color.print();
                }

                x = x + 1;
            }

            System.out.write(10);

            y = y - 1;
        }
    }
}

class Triangle {
    public Vec3 vertex0;
    public Vec3 vertex1;
    public Vec3 vertex2;
    public Color color;

    public Triangle init(Vec3 vertex0, Vec3 vertex1, Vec3 vertex2, Color color) {
        this.vertex0 = vertex0;
        this.vertex1 = vertex1;
        this.vertex2 = vertex2;
        this.color = color;

        return this;
    }
}

class Vec3 {
    public Frac x;
    public Frac y;
    public Frac z;

    public Frac tmp;

    public Vec3 init(Frac x, Frac y, Frac z) {
        this.x = x;
        this.y = y;
        this.z = z;

        this.tmp = new Frac().initInt(0);

        return this;
    }

    public Vec3 initInts(int x, int y, int z) {
        return this.init(
                new Frac().initInt(x),
                new Frac().initInt(y),
                new Frac().initInt(z)
        );
    }

    public Vec3 initVec(Vec3 other) {
        this.x.initFrac(other.x);
        this.y.initFrac(other.y);
        this.z.initFrac(other.z);
        return this;
    }

    public void add(Vec3 other) {
        this.x.add(other.x);
        this.y.add(other.y);
        this.z.add(other.z);
    }

    public void subtract(Vec3 other) {
        this.x.subtract(other.x);
        this.y.subtract(other.y);
        this.z.subtract(other.z);
    }

    public void cross(Vec3 a, Vec3 b) {
        this.x.initFrac(a.y);
        this.x.times(b.z);
        this.tmp.initFrac(a.z);
        this.tmp.times(b.y);
        this.x.subtract(this.tmp);

        this.y.initFrac(a.z);
        this.y.times(b.x);
        this.tmp.initFrac(a.x);
        this.tmp.times(b.z);
        this.y.subtract(this.tmp);

        this.z.initFrac(a.x);
        this.z.times(b.y);
        this.tmp.initFrac(a.y);
        this.tmp.times(b.x);
        this.z.subtract(this.tmp);
    }

    public void dot(Vec3 other, Frac target) {
        target.initFrac(this.x);
        target.times(other.x);

        this.tmp.initFrac(this.y);
        this.tmp.times(other.y);
        target.add(this.tmp);

        this.tmp.initFrac(this.z);
        this.tmp.times(other.z);
        target.add(this.tmp);
    }

    public void timesFrac(Frac v) {
        this.x.times(v);
        this.y.times(v);
        this.z.times(v);
    }
}

class Frac {
    public int numerator;
    public int denominator;

    public Frac init(int numerator, int denominator) {
        int gcd = gcd(numerator, denominator);

        if (gcd == 0) {
            this.numerator = 0;
            this.denominator = 1;
            return this;
        }

        this.numerator = numerator / gcd;
        this.denominator = denominator / gcd;

        if (this.denominator < 0) {
            this.numerator = this.numerator * -1;
            this.denominator = this.denominator * -1;
        }

        /* limit numerator and denominator in order to avoid artifacts */
        int limit = 32768;

        while (this.numerator > (limit - 1) || this.numerator < -limit || this.denominator > (limit - 1)) {
            this.numerator = this.numerator / 2;
            this.denominator = this.denominator / 2;
        }

        return this;
    }

    public Frac initFrac(Frac other) {
        return this.init(other.numerator, other.denominator);
    }

    public int gcd(int a, int b) {
        while (b != 0) {
            int tmp = a % b;
            a = b;
            b = tmp;
        }
        return a;
    }

    public Frac initInt(int i) {
        return this.init(i, 1);
    }

    public void add(Frac other) {
        this.init(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator);
    }

    public void subtract(Frac other) {
        this.init(numerator * other.denominator - other.numerator * denominator, denominator * other.denominator);
    }

    public void times(Frac other) {
        this.init(numerator * other.numerator, denominator * other.denominator);
    }

    public void invert() {
        this.init(denominator, numerator);
    }

    public boolean lt(Frac other) {
        return numerator * other.denominator < other.numerator * denominator;
    }

    public boolean gt(Frac other) {
        return numerator * other.denominator > other.numerator * denominator;
    }
}

class Color {
    public int r;
    public int g;
    public int b;

    public Color init_c(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;

        return this;
    }

    public void print() {
        System.out.println(r);
        System.out.println(g);
        System.out.println(b);
    }
}