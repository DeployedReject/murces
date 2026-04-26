# Variables
CC = gcc
CFLAGS = -Wall -Wextra -g
TARGET = commandsTester
SRCS = commands.c 
OBJS = $(SRCS:.c=.o)

# Default rule: build the executable
all: $(TARGET)

# Link objects to create the executable
$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $(TARGET) $(OBJS)

# Compile source files into object files
%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

# Clean up build files
clean:
	rm -f $(OBJS) $(TARGET)

# Phony targets (not actual files)
.PHONY: all clean
