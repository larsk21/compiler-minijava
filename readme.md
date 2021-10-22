Java Compiler for the MiniJava language

# Setup

Our project requires the following tools with the specified versions.

| Tool  | Version |
|-------|---------|
| Java  | >= 8    |
| Maven | 3       |

The scripts *(see below)* require a bash environment (in order to find the project directory).

# Build

Execute the build script in the root directory from anywhere you like (without any arguments).

`./build`

`~/compiler/build`

# Run

Execute the run script in the root directory from anywhere you like with the arguments for the compiler.

`./run --echo ./example/Test.java`

`~/compiler/run --echo Foo.java`
