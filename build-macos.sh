#!/bin/bash
#
# UberBukkit macOS Build Script
# Compiles the server and copies the JAR to the server directory
#

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Java 17 required for Gradle plugins
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home

# Directories
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="/Users/eric/Developer/server"
OUTPUT_JAR="$SCRIPT_DIR/build/libs/uberbukkit-2.0.2.jar"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  UberBukkit macOS Build Script${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if Gradle wrapper exists
if [ ! -f "$SCRIPT_DIR/gradlew" ]; then
    echo -e "${RED}Error: gradlew not found in $SCRIPT_DIR${NC}"
    exit 1
fi

# Make gradlew executable
chmod +x "$SCRIPT_DIR/gradlew"

# Change to project directory
cd "$SCRIPT_DIR"

# Clean and build
echo -e "${YELLOW}Cleaning previous build...${NC}"
./gradlew clean

echo ""
echo -e "${YELLOW}Building UberBukkit...${NC}"
./gradlew shadowJar

# Check if build succeeded
if [ ! -f "$OUTPUT_JAR" ]; then
    # Try to find the jar with a different name pattern
    OUTPUT_JAR=$(find "$SCRIPT_DIR/build/libs" -name "*.jar" ! -name "*-original.jar" -print -quit 2>/dev/null)
    if [ -z "$OUTPUT_JAR" ] || [ ! -f "$OUTPUT_JAR" ]; then
        echo -e "${RED}Error: Build output not found!${NC}"
        echo "Looking in: $SCRIPT_DIR/build/libs/"
        ls -la "$SCRIPT_DIR/build/libs/" 2>/dev/null || echo "Directory does not exist"
        exit 1
    fi
fi

echo ""
echo -e "${GREEN}Build successful!${NC}"
echo "Output: $OUTPUT_JAR"

# Create server directory if it doesn't exist
if [ ! -d "$SERVER_DIR" ]; then
    echo -e "${YELLOW}Creating server directory: $SERVER_DIR${NC}"
    mkdir -p "$SERVER_DIR"
fi

# Copy to server directory
echo ""
echo -e "${YELLOW}Copying to server directory...${NC}"
cp "$OUTPUT_JAR" "$SERVER_DIR/server.jar"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Build Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Server JAR: $SERVER_DIR/server.jar"
echo ""
echo "To run the server:"
echo "  cd $SERVER_DIR"
echo "  java -jar server.jar"
echo ""
