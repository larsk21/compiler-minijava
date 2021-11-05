package edu.kit.compiler.logger;

import java.util.Optional;

public class Logger {
    private final Optional<String> name;
    private final Verbosity verbosity;

    public Logger() {
        this(Verbosity.Default);
    }

    public Logger(Verbosity verbosity) {
        this.name = Optional.empty();
        this.verbosity = verbosity;
    }

    public Logger(String name, Verbosity verbosity) {
        this.name = Optional.ofNullable(name);
        this.verbosity = verbosity;
    }

    public static Logger nullLogger() {
        return new Logger(Verbosity.Silent);
    }

    public Logger withName(String name) {
        return new Logger(name, verbosity);
    }

    public Optional<String> getName() {
        return name;
    }

    public Verbosity getVerbosity() {
        return verbosity;
    }

    public void info(String format, Object... args) {
        log(Level.INFO, String.format(format, args));
    }

    public void info(int line, int column, String format, Object... args) {
        log(Level.INFO, line, column, String.format(format, args));
    }

    public void warn(String format, Object... args) {
        log(Level.WARN, String.format(format, args));
    }

    public void warn(int line, int column, String format, Object... args) {
        log(Level.WARN, line, column, String.format(format, args));
    }

    public void error(String format, Object... args) {
        log(Level.ERROR, String.format(format, args));
    }

    public void error(int line, int column, String format, Object... args) {
        log(Level.ERROR, line, column, String.format(format, args));
    }

    private void log(Level level, String message) {
        if (verbosity.compareTo(level.verbosity) >= 0) {
            System.err.printf("%s%s%s%n", level.getPrefix(), getNamePrefix(), message);
        }
    }

    private void log(Level level, int line, int column, String message) {
        if (verbosity.compareTo(level.verbosity) >= 0) {
            System.err.printf("%s%s%d, %d: %s%n", level.getPrefix(), getNamePrefix(), message);
        }
    }

    private String getNamePrefix() {
        return name.map(name -> name + ": ").orElse("");
    }

    // ! Order of verbosity levels is defined by order of enum entries
    public static enum Verbosity {
        Silent,
        Quiet,
        Default,
        Verbose,
        Debug;
    }

    private static enum Level {
        INFO("info", Verbosity.Verbose),
        WARN("warning", Verbosity.Default),
        ERROR("error", Verbosity.Quiet);

        private final String name;
        private final Verbosity verbosity;

        private Level(String name, Verbosity verbosity) {
            this.name = name;
            this.verbosity = verbosity;
        }

        public String getPrefix() {
            return name + ": ";
        }
    }
}
