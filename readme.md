# Java Compiler for the MiniJava language

## Setup

Our project requires the following tools with the specified versions.

| Tool  | Version |
|-------|---------|
| Java  | >= 14   |
| Maven | 3       |

The scripts *(see below)* require a bash environment (in order to find the project directory).

### GitHub Dependencies

| Name   | Website             | Repo                                       |
|--------|---------------------|--------------------------------------------|
| Firm   | http://libfirm.org/ | https://pp.ipd.kit.edu/git/libfirm         |
| MJTest | *n/a*               | https://git.scc.kit.edu/IPDSnelting/mjtest |

## Build

Execute the build script in the root directory from anywhere you like.
Arguments to the build script are passed to Maven (for example `-DskipTests`).

`./build [maven arguments]`

## Run

Execute the run script in the root directory from anywhere you like with the arguments for the compiler.

### CLI usage:
```
Java Easy Compiler

usage: compiler [<action>] [<optimization-level>] [<output-verbosity>] [<debug options>]

Action
 -e --echo <path>           output file contents
 -l --lextest <path>        output the tokens from the lexer
 -p --parsetest <path>      try to parse the file contents
 -a --print-ast <path>      try to parse the file contents and output the AST
 -c --check <path>          try to parse the file contents and perform semantic analysis
 -f --compile-firm <path>   transform the file to Firm IR and compile it using the Firm backend
 -co --compile <path>       compile the file (default)

Optimization Level
 -O0 --optimize0            run (almost) no optimizations
 -O1 --optimize1            run standard optimizations (default)

Output Verbosity
 -v --verbose               be more verbose
 -d --debug                 print debug information

Debug Options
 -dg --dump-graphs          dump the Firm graphs of all methods

Help
 -h --help                  print command line syntax help

for more information check out: https://github.com/larsk21/compiler-minijava
```

## Optimizations

Our compiler includes the optimizations listed below.
The optimizations are assigned to two levels, enabled with `-O0` and `-O1`, with level 1 being the default.
All optimizations run in level 0 are also run in level 1.

Additionally, the compiler uses a more advanced register allocator in level 1.

### Middle End Optimizations

| Optimization                  | Minimum Optimization Level |
|-------------------------------|----------------------------|
| Constant Propagation          | 0                          |
| Arithmetic Identities         | 0                          |
| Trivial Jumps & Linear Blocks | 1                          |
| Arithmetic Strength Reduction | 1                          |
| Pure Functions                | 1                          |
| Inliner                       | 1                          |
| Loop Invariant Code Motion    | 1                          |
| Loop Unrolling                | 1                          |
| Unused Arguments              | 1                          |

### Backend Optimizations

| Optimization   | Minimum Optimization Level |
|----------------|----------------------------|
| Jump Inversion | 1                          |
