class Ackermann
{

    public int ack(int m, int n)
    {
        if (m == 0)
        {
            return n + 1;
        }
        else if((m > 0) && (n == 0))
        {
            return ack(m - 1, 1);
        }
        else if((m > 0) && (n > 0))
        {
            return ack(m - 1, ack(m, n - 1));
        }else {
            System.out.println(m);
            return n + 1;
        }
    }

    public static void main(String[] args)
    {
        Ackermann a = new Ackermann();
        System.out.println(a.ack(2,1));
        System.out.println(a.ack(2,2));
        System.out.println(a.ack(3,3));
        System.out.println(a.ack(3,8));
    }
}
