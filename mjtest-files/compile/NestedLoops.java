class Main {
	public static void main(String[] args) {
		int i = 97;
		while (i < 123) {
			int j = 97;
			while (j < 123) {
				int k = 97;
				while (k < 123) {
					int l = 97;
					while (l < 123) {
						System.out.write(i);
						System.out.write(j);
						System.out.write(k);
						System.out.write(l);
						System.out.write(10);
						l = l + 1;
					}
					k = k + 1;
				}
				j = j + 1;
			}
			i = i + 1;
		}

		System.out.flush();

		i = 0;
		while (i < 100) {
			int j = 0;
			while (j < i) {
				System.out.write(42);
				j = j + 1;
			}
			System.out.write(10);
			i = i + 1;
		}
		System.out.flush();
	}
}
