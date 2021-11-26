#include <stdio.h>

int read()
{
    int c = getchar();
    if (c == EOF) {
        return -1;
    }
    return c;
}

void print(int value)
{
    printf("%i\n", value);
}

void write(int byte)
{
    putchar(byte);
}

void flush()
{
    fflush(stdout);
}
