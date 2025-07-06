#!/bin/bash

# Cloudy Library Screenshot Tests Runner for API 27
# Test Native RenderScript Toolkit on x86_64 emulator only

set -e

echo "🔧 Starting Cloudy Library Screenshot Tests for API 27..."

# Color definitions
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# API level configuration (API 27 only)
API_LEVEL=27

print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if running on x86_64 architecture
check_architecture() {
    print_status "Checking system architecture..."
    
    local arch
    arch=$(uname -m)
    if [[ "$arch" != "x86_64" ]]; then
        print_error "API 27 requires x86_64 architecture. Current architecture: $arch"
        print_status "Please run this script on an x86_64 system or use the main script for other API levels."
        exit 1
    fi
    
    print_success "x86_64 architecture confirmed"
}

# Comprehensive setup check
check_setup() {
    print_status "Performing comprehensive setup check..."
    
    # Check if running from project root
    if [[ ! -f "./gradlew" ]]; then
        print_error "gradlew not found. Please run this script from the project root directory."
        exit 1
    fi
    
    # Check Android SDK environment
    check_android_sdk
    
    # Check required tools
    check_required_tools
    
    # Check system requirements
    check_system_requirements
    
    print_success "Setup check completed successfully"
}

# Check Android SDK environment
check_android_sdk() {
    print_status "Checking Android SDK environment..."
    
    # Check if ANDROID_HOME or ANDROID_SDK_ROOT is set
    if [[ -z "$ANDROID_HOME" && -z "$ANDROID_SDK_ROOT" ]]; then
        print_error "Android SDK environment variables not set"
        print_status "Please set ANDROID_HOME or ANDROID_SDK_ROOT"
        print_status "Example: export ANDROID_HOME=/path/to/android/sdk"
        exit 1
    fi
    
    # Set SDK path
    if [[ -n "$ANDROID_SDK_ROOT" ]]; then
        SDK_PATH="$ANDROID_SDK_ROOT"
    else
        SDK_PATH="$ANDROID_HOME"
    fi
    
    # Check if SDK directory exists
    if [[ ! -d "$SDK_PATH" ]]; then
        print_error "Android SDK directory not found: $SDK_PATH"
        exit 1
    fi
    
    # Check if SDK tools exist
    if [[ ! -f "$SDK_PATH/cmdline-tools/latest/bin/sdkmanager" ]]; then
        print_error "sdkmanager not found at $SDK_PATH/cmdline-tools/latest/bin/sdkmanager"
        print_status "Please install Android SDK Command-line Tools"
        print_status "You can install it via Android Studio or download from Google"
        exit 1
    fi
    
    # Add SDK tools to PATH
    export PATH="$SDK_PATH/cmdline-tools/latest/bin:$SDK_PATH/emulator:$SDK_PATH/platform-tools:$PATH"
    
    print_success "Android SDK environment verified"
}

# Check required tools
check_required_tools() {
    print_status "Checking required tools..."
    
    local missing_tools=()
    
    # Check sdkmanager
    if ! command -v sdkmanager &> /dev/null; then
        missing_tools+=("sdkmanager")
    fi
    
    # Check avdmanager
    if ! command -v avdmanager &> /dev/null; then
        missing_tools+=("avdmanager")
    fi
    
    # Check emulator
    if ! command -v emulator &> /dev/null; then
        missing_tools+=("emulator")
    fi
    
    # Check adb
    if ! command -v adb &> /dev/null; then
        missing_tools+=("adb")
    fi
    
    if [[ ${#missing_tools[@]} -gt 0 ]]; then
        print_error "Missing required tools: ${missing_tools[*]}"
        print_status "Please ensure Android SDK Command-line Tools are properly installed"
        exit 1
    fi
    
    print_success "All required tools are available"
}

# Check system requirements
check_system_requirements() {
    print_status "Checking system requirements..."
    
    # Check available memory (at least 4GB recommended)
    local available_memory=0
    if command -v free &> /dev/null; then
        # Linux
        available_memory=$(free -m | awk 'NR==2{printf "%.0f", $7/1024}')
    elif command -v vm_stat &> /dev/null; then
        # macOS
        # Get page size and free pages, then calculate GB
        page_size=$(vm_stat | grep "page size" | awk '{print $8}')
        free_pages=$(vm_stat | awk '/Pages free/ {gsub(/\./, "", $3); print $3}')
        available_memory=$(echo "scale=0; ($free_pages * $page_size) / 1024 / 1024 / 1024" | bc)
    fi
    
    if [[ $available_memory -lt 4 ]]; then
        print_warning "Low available memory: ${available_memory}GB (4GB+ recommended)"
    fi
    
    # Check available disk space (at least 10GB)
    local available_disk
    available_disk=$(df . | awk 'NR==2{printf "%.0f", $4/1024/1024}')
    if [[ $available_disk -lt 10 ]]; then
        print_warning "Low available disk space: ${available_disk}GB (10GB+ recommended)"
    fi
    
    # Check if running on supported OS
    if [[ "$OSTYPE" != "linux-gnu"* && "$OSTYPE" != "darwin"* ]]; then
        print_warning "Unsupported operating system: $OSTYPE"
        print_status "This script is tested on Linux and macOS"
    fi
    
    print_success "System requirements check completed"
}

# Check emulator status
check_emulator() {
    print_status "Checking emulator for API $API_LEVEL..."
    
    if ! command -v emulator &> /dev/null; then
        print_error "Android SDK emulator not found in PATH"
        exit 1
    fi
    
    # Check AVD list
    local avd_name="test_avd_${API_LEVEL}"
    if ! emulator -list-avds | grep -q "$avd_name"; then
        print_warning "AVD $avd_name not found. Creating..."
        create_avd $avd_name
    fi
    
    return 0
}

# Create AVD
create_avd() {
    local avd_name=$1
    
    print_status "Creating AVD: $avd_name"
    
    # Use x86_64 architecture for API 27
    local arch="x86_64"
    
    # Use default target for faster boot times
    local system_image="system-images;android-${API_LEVEL};default;${arch}"
    
    # Download SDK image (if needed)
    print_status "Downloading system image for API $API_LEVEL ($arch)..."
    yes | sdkmanager "$system_image"
    
    # Create AVD
    print_status "Creating AVD with system image..."
    echo "no" | avdmanager create avd \
        -n "$avd_name" \
        -k "$system_image" \
        -d "pixel_xl" \
        --force
        
    print_success "AVD $avd_name created successfully"
}

# Start emulator
start_emulator() {
    local avd_name="test_avd_${API_LEVEL}"
    
    print_status "Starting emulator for API $API_LEVEL..."
    
    # Check if emulator is already running
    if adb devices | grep -q "emulator"; then
        print_warning "Emulator already running. Stopping existing emulator..."
        adb emu kill
        sleep 5
    fi
    
    # Start emulator (background) with optimized settings for API 27
    emulator -avd "$avd_name" \
        -no-window \
        -gpu swiftshader_indirect \
        -noaudio \
        -no-boot-anim \
        -camera-back none \
        -memory 2048 \
        -partition-size 2048 \
        -accel off \
        -no-snapshot &
    
    local emulator_pid=$!
    print_status "Emulator started with PID: $emulator_pid"
    
    # Wait for emulator boot completion with longer timeout
    print_status "Waiting for emulator to boot (this may take several minutes)..."
    local boot_timeout=600  # 10 minutes timeout
    local elapsed=0
    
    while [[ $elapsed -lt $boot_timeout ]]; do
        if adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; then
            break
        fi
        sleep 5
        elapsed=$((elapsed + 5))
        if [[ $((elapsed % 60)) -eq 0 ]]; then
            print_status "Still waiting for boot... (${elapsed}s elapsed)"
        fi
    done
    
    if [[ $elapsed -ge $boot_timeout ]]; then
        print_error "Emulator boot timeout after ${boot_timeout}s"
        return 1
    fi
    
    print_status "Emulator booted in ${elapsed}s"
    
    # Wait a bit more for system to stabilize
    sleep 10
    
    # Unlock screen
    adb shell input keyevent 82
    
    # Set resolution
    adb shell wm size 1080x1920
    adb shell wm density 440
    
    print_success "Emulator API $API_LEVEL is ready!"
    echo $emulator_pid
}

# Run tests
run_tests() {
    print_status "Running Dropshots tests for API $API_LEVEL..."
    
    # Execute tests
    ./gradlew :app:connectedDebugAndroidTest \
        --no-daemon \
        --no-build-cache \
        -Pandroid.testInstrumentationRunnerArguments.class=com.skydoves.cloudydemo.MainTest \
        || {
            print_error "Tests failed for API $API_LEVEL"
            return 1
        }
    
    # Collect screenshots
    collect_screenshots
    
    print_success "Tests completed successfully for API $API_LEVEL"
}

# Collect screenshots
collect_screenshots() {
    local output_dir="screenshots/api-$API_LEVEL"
    
    print_status "Collecting screenshots for API $API_LEVEL..."
    
    mkdir -p "$output_dir"
    
    # Collect screenshots from multiple locations
    adb pull /sdcard/dropshots/ "$output_dir/" 2>/dev/null || true
    adb pull /storage/emulated/0/dropshots/ "$output_dir/" 2>/dev/null || true
    
    # Also collect from build outputs
    find ./app/build/outputs -name "*.png" -type f -exec cp {} "$output_dir/" \; 2>/dev/null || true
    
    # Clean up filenames
    cd "$output_dir"
    for file in *.png; do
        if [[ -f "$file" && "$file" != *"api$API_LEVEL"* ]]; then
            base_name="${file%.png}"
            mv "$file" "${base_name}_api${API_LEVEL}.png" 2>/dev/null || true
        fi
    done 2>/dev/null || true
    cd - > /dev/null
    
    local count
    count=$(find "$output_dir" -name "*.png" 2>/dev/null | wc -l)
    print_success "Collected $count screenshots for API $API_LEVEL"
}

# Clean up emulator
cleanup_emulator() {
    print_status "Cleaning up emulator..."
    adb emu kill 2>/dev/null || true
    sleep 2
}

# Main execution logic
main() {
    print_status "Cloudy Library Screenshot Testing for API 27"
    print_status "Testing Native RenderScript Toolkit on x86_64 Android emulator"
    echo ""
    
    # Check architecture first
    check_architecture
    
    # Check permissions
    if [[ ! -w "." ]]; then
        print_error "No write permission in current directory"
        exit 1
    fi
    
    # Check Gradle wrapper
    if [[ ! -f "./gradlew" ]]; then
        print_error "gradlew not found. Run from project root directory."
        exit 1
    fi
    
    # Perform comprehensive setup check
    check_setup
    
    # Create screenshots directory
    mkdir -p screenshots
    
    # Execute API 27 tests
    print_status "========== API Level $API_LEVEL =========="
    
    check_emulator
    emulator_pid=$(start_emulator)
    
    # Run tests
    if run_tests; then
        print_success "API $API_LEVEL tests completed successfully"
    else
        print_error "API $API_LEVEL tests failed"
    fi
    
    # Clean up emulator
    cleanup_emulator
    
    # Result summary
    echo ""
    print_success "🎉 Screenshot testing completed for API $API_LEVEL!"
    print_status "📁 Screenshots saved in: ./screenshots/api-$API_LEVEL/"
    
    # Display number of collected screenshots
    total_screenshots=$(find "./screenshots/api-$API_LEVEL" -name "*.png" 2>/dev/null | wc -l)
    print_status "📊 Total screenshots captured: $total_screenshots"
}

# Ctrl+C handler
trap 'print_warning "Script interrupted. Cleaning up..."; cleanup_emulator; exit 1' INT

# Display usage
if [[ "$1" == "-h" || "$1" == "--help" ]]; then
    echo "Usage: $0"
    echo ""
    echo "API 27 Screenshot Testing Script for Cloudy Library"
    echo ""
    echo "This script runs Dropshots screenshot tests specifically for API 27 (Android 8.1)"
    echo "on x86_64 architecture only."
    echo ""
    echo "Prerequisites:"
    echo "  - x86_64 architecture system"
    echo "  - Android SDK installed with ANDROID_HOME or ANDROID_SDK_ROOT set"
    echo "  - Android SDK Command-line Tools installed"
    echo "  - Run from project root directory"
    echo ""
    echo "Setup Instructions:"
    echo "  1. Install Android Studio or Android SDK"
    echo "  2. Set ANDROID_HOME environment variable:"
    echo "     export ANDROID_HOME=/path/to/android/sdk"
    echo "  3. Install Android SDK Command-line Tools via Android Studio"
    echo "  4. Ensure you have at least 4GB RAM and 10GB free disk space"
    echo ""
    echo "Note: This script is specifically for API 27. For other API levels,"
    echo "use the main script: ./scripts/run-screenshot-tests.sh"
    exit 0
fi

# Execute main function
main 