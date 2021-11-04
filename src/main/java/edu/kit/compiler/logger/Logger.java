package edu.kit.compiler.logger;

import java.util.Optional;

public class Logger {
    private final Optional<String> name;

    public Logger() {
        this.name = Optional.empty();
    }

    public Logger(String name) {
        this.name = Optional.ofNullable(name);
    }

    public void info(String format, Object... args) {
        Level.INFO.log(getNamePrefix(), String.format(format, args));
    }

    public void info(int line, int column, String format, Object... args) {
        Level.INFO.log(getNamePrefix(), line, column, String.format(format, args));
    }

    public void warn(String format, Object... args) {
        Level.WARN.log(getNamePrefix(), String.format(format, args));
    }

    public void warn(int line, int column, String format, Object... args) {
        Level.WARN.log(getNamePrefix(), line, column, String.format(format, args));
    }

    public void error(String format, Object... args) {
        Level.ERROR.log(getNamePrefix(), String.format(format, args));
    }

    public void error(int line, int column, String format, Object... args) {
        Level.ERROR.log(getNamePrefix(), line, column, String.format(format, args));
    }

    private String getNamePrefix() {
        return name.map(name -> name + ": ").orElse("");
    }

    private static enum Level {
        INFO("info"),
        WARN("warning"),
        ERROR("error");

        private final String name;

        private Level(String name) {
            this.name = name;
        }

        private void log(String namePrefix, String message) {
            System.err.printf(getPrefix() + namePrefix + message);
        }

        private void log(String namePrefix, int line, int column, String message) {
            System.err.printf("%s%s%d,%d: %s\n", getPrefix(), namePrefix, line, column, message);
        }

        private String getPrefix() {
            return name + ": ";
        }
    }
}
