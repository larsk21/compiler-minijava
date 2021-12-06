class Factorial{

    public static void main(String[] s) {
        int i = 1;
        int fact = 1;
        int number=5;
        while(i < number) {
            fact=fact*i;
            i = i+1;
        }
        System.out.println(fact);
    }

}