package edu.kit.compiler.codegen.pattern;

public interface Match {
    public boolean matches();

    public static abstract class None implements Match {
        @Override
        public boolean matches() {
            return false;
        }
    }

    public static abstract class Some implements Match {
        @Override
        public boolean matches() {
            return true;
        }
    }
}
