package edu.kit.compiler.logger;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Optional;

public class Logger {
    private final Optional<String> name;
    private final Verbosity verbosity;
    private final boolean printColor;
    private final PrintStream stream;

    public Logger() {
        this(Verbosity.Default, false);
    }

    public Logger(Verbosity verbosity, boolean printColor) {
        this(null, verbosity, printColor);
    }

    public Logger(String name, Verbosity verbosity, boolean printColor) {
        this(name, verbosity, printColor, System.err);
    }

    public Logger(String name, Verbosity verbosity, boolean printColor, PrintStream stream) {
        this.name = Optional.ofNullable(name);
        this.verbosity = verbosity;
        this.printColor = printColor;
        this.stream = stream;
    }

    public static Logger nullLogger() {
        return new Logger(null, Verbosity.Silent, false,
            new PrintStream(OutputStream.nullOutputStream()));
    }

    public Logger withName(String name) {
        return new Logger(name, verbosity, printColor);
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
            var namePrefix = name.map(name -> " " + name + ":").orElse("");
            stream.printf("%s:%s %s%n", level.getPrefix(printColor),
                namePrefix, message);
        }
    }

    private void log(Level level, int line, int column, String message) {
        if (verbosity.compareTo(level.verbosity) >= 0) {
            var namePrefix = name.map(name -> " " + name + " at").orElse("");
            stream.printf("%s:%s line %d, column %d: %s%n", level.getPrefix(printColor),
                namePrefix, line, column, message);
        }
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
        INFO("info", Verbosity.Verbose, "\u001B[32m"),
        WARN("warning", Verbosity.Default, "\u001B[33m"),
        ERROR("error", Verbosity.Quiet, "\u001B[91m");

        private static final String ANSI_RESET = "\u001B[0m";

        private final String name;
        private final Verbosity verbosity;
        private final String color;

        private Level(String name, Verbosity verbosity, String color) {
            this.name = name;
            this.verbosity = verbosity;
            this.color = color;
        }

        public String getPrefix(boolean printColor) {
            if (printColor) {
                return color + name + ANSI_RESET;
            } else {
                return name;
            }
        }
    }
}
