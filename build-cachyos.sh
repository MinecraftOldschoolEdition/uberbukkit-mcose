#!/usr/bin/env bash
#
# UberBukkit CachyOS/Linux Build Script
# Compiles the server and copies the JAR to the server directory.
#

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="${SERVER_DIR:-$HOME/Developer/server}"
SERVER_JAR="${SERVER_DIR}/server.jar"
OUTPUT_JAR=""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  UberBukkit CachyOS Build Script${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if Gradle wrapper exists
if [ ! -f "$SCRIPT_DIR/gradlew" ]; then
    echo -e "${RED}Error: gradlew not found in $SCRIPT_DIR${NC}"
    exit 1
fi

# Resolve JAVA_HOME if needed
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
    if command -v java >/dev/null 2>&1; then
        JAVA_BIN="$(command -v java)"
        JAVA_REAL="$(readlink -f "$JAVA_BIN" 2>/dev/null || true)"
        if [ -z "$JAVA_REAL" ]; then
            JAVA_REAL="$JAVA_BIN"
        fi
        export JAVA_HOME="$(cd "$(dirname "$JAVA_REAL")/.." && pwd)"
    else
        echo -e "${RED}Error: Java not found. Install JDK 17+ and/or set JAVA_HOME.${NC}"
        exit 1
    fi
fi

JAVA_VERSION_LINE="$("$JAVA_HOME/bin/java" -version 2>&1 | head -n1)"
JAVA_MAJOR="$(echo "$JAVA_VERSION_LINE" | sed -E 's/.*version "(1\.)?([0-9]+).*/\2/')"

if ! [[ "$JAVA_MAJOR" =~ ^[0-9]+$ ]]; then
    echo -e "${RED}Error: Could not parse Java version from: $JAVA_VERSION_LINE${NC}"
    exit 1
fi

if [ "$JAVA_MAJOR" -lt 17 ]; then
    echo -e "${RED}Error: Java 17+ required (found: $JAVA_VERSION_LINE)${NC}"
    exit 1
fi

echo -e "${YELLOW}Using JAVA_HOME: $JAVA_HOME${NC}"
echo -e "${YELLOW}Using Java: $JAVA_VERSION_LINE${NC}"

# Make gradlew executable
chmod +x "$SCRIPT_DIR/gradlew"

# Change to project directory
cd "$SCRIPT_DIR"

# Clean and build
echo ""
echo -e "${YELLOW}Cleaning previous build...${NC}"
./gradlew clean

echo ""
echo -e "${YELLOW}Building UberBukkit...${NC}"
./gradlew shadowJar

# Locate output JAR
OUTPUT_JAR="$(find "$SCRIPT_DIR/build/libs" -maxdepth 1 -type f -name "uberbukkit*.jar" ! -name "*-original.jar" -print | sort | tail -n1)"
if [ -z "$OUTPUT_JAR" ] || [ ! -f "$OUTPUT_JAR" ]; then
    OUTPUT_JAR="$(find "$SCRIPT_DIR/build/libs" -maxdepth 1 -type f -name "*.jar" ! -name "*-original.jar" -print | sort | tail -n1)"
fi

if [ -z "$OUTPUT_JAR" ] || [ ! -f "$OUTPUT_JAR" ]; then
    echo -e "${RED}Error: Build output not found!${NC}"
    echo "Looking in: $SCRIPT_DIR/build/libs/"
    ls -la "$SCRIPT_DIR/build/libs/" 2>/dev/null || echo "Directory does not exist"
    exit 1
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
cp -f "$OUTPUT_JAR" "$SERVER_JAR"

if [ ! -f "$SERVER_JAR" ]; then
    echo -e "${RED}Error: Failed to copy output to $SERVER_JAR${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Build Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Server JAR: $SERVER_JAR"
echo ""
echo "To run the server:"
echo "  cd $SERVER_DIR"
echo "  java -jar server.jar"
echo ""
