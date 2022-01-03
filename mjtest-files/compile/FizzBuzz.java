class FizzBuzz
/*MiniJava Version of FizzBuzz with negative numbers as replacement*/
{
    public static void main(String[] args) {
        int i = 1;
        while (i <= 100) {
            if (i % 3 == 0 && i % 5 == 0) {
                System.out.println(-3);
            } else if (i % 3 == 0) {
                System.out.println(-1);
            } else if (i % 5 == 0) {
                System.out.println(-2);
            } else {
                System.out.println(i);
            }
            i = i + 1;
        }
    }
}
