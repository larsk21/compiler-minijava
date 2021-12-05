class LinkedList {
    public static void main(String[] args) {
        List list = new List();
        list.init();

        int i = 0;
        while (i < 100) {
            list.insert(i);
            i = i + 1;
        }

        int[] result = list.intoArray();
        int j = 0;
        while (j < list.size) {
            System.out.println(result[j]);
            j = j + 1;
        }
    }
}

class Node {
    public int val;
    public Node next;
}

class List {
    public Node node;
    public int size;

    public void init() {
        this.node = new Node();
    }

    public void insert(int val) {
        Node start = node;
        node = new Node();
        node.val = val;
        node.next = start;
        size = size + 1;
    }

    public int[] intoArray() {
        int[] result = new int[size];
        int i = size;
        Node current = node;
        while (i > 0) {
            i = i - 1;
            result[i] = current.val;
            current = current.next;
        }
        return result;
    }
}
