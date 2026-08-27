#!/usr/bin/env bash
set -e

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

cleanup() {
  echo ""
  echo "Stopping frontend and backend..."
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
}

trap cleanup EXIT INT TERM

echo "Building backend..."
cd "$ROOT_DIR/ebic"

mvn -q package -DskipTests

CP="target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)"

echo "Starting backend on http://localhost:7070 ..."
java -cp "$CP" com.walnutt.web.WebServer &
BACKEND_PID=$!

echo "Starting frontend on http://localhost:5173 ..."
cd "$ROOT_DIR/web"

npm run dev &
FRONTEND_PID=$!

echo ""
echo "Backend:  http://localhost:7070"
echo "Frontend: http://localhost:5173"
echo ""
echo "Press Ctrl+C to stop both."

wait