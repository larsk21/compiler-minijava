Java Compiler for the MiniJava language

# Setup

Our project requires the following tools with the specified versions.

| Tool  | Version |
|-------|---------|
| Java  | >= 8    |
| Maven | 3       |

The build script (see below) requires a bash environment.

# Build

Execute the build script in the root directory (without any arguments).

`./build`

# Run

Execute the run script in the root directory from anywhere you like with the arguments for the compiler.

`./run --echo ./example/Test.java`

`~/compiler/run --echo Foo.java`
