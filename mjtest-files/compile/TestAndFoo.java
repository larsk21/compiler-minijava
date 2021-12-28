class Test {
	
	public int i;
	
	
	public static void main(String[] args) {
		Test2 test = new Test2();
		
		test.i = 2;
		
		Test2 test2 = new Test2();
		
		test2.i = 4;
		
		test.foo(test2);
	}
}

class Test2 {
	
	public int i;
	
	public void foo(Test2 test) {
		while (i > 0 && test.getValue() > 0) {
			System.out.println(getValue());
		}
		
		if (test.i > 0) {
			System.out.println(test.i);
		} else {
			System.out.println(24);
		}
	}
	
	public int getValue() {
		int j = i;
		i = i - 1;
		return j;
	}
}

