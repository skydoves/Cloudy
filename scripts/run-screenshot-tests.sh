#!/bin/bash

# Cloudy Library Screenshot Tests Runner
# Test Native RenderScript Toolkit on actual emulators

set -e

echo "🔧 Starting Cloudy Library Screenshot Tests..."

# Color definitions
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# API level configuration
API_LEVELS=(27 30 33)
SELECTED_API=${1:-"all"}

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
    local available_memory=$(free -m | awk 'NR==2{printf "%.0f", $7/1024}')
    if [[ $available_memory -lt 4 ]]; then
        print_warning "Low available memory: ${available_memory}GB (4GB+ recommended)"
    fi
    
    # Check available disk space (at least 10GB)
    local available_disk=$(df . | awk 'NR==2{printf "%.0f", $4/1024/1024}')
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
    local api_level=$1
    print_status "Checking emulator for API $api_level..."
    
    if ! command -v emulator &> /dev/null; then
        print_error "Android SDK emulator not found in PATH"
        exit 1
    fi
    
    # Check AVD list
    local avd_name="test_avd_${api_level}"
    if ! emulator -list-avds | grep -q "$avd_name"; then
        print_warning "AVD $avd_name not found. Creating..."
        create_avd $api_level $avd_name
    fi
    
    return 0
}

# Create AVD
create_avd() {
    local api_level=$1
    local avd_name=$2
    
    print_status "Creating AVD: $avd_name"
    
    # Download SDK image (if needed)
    print_status "Downloading system image for API $api_level..."
    sdkmanager "system-images;android-${api_level};google_apis;x86_64"
    
    # Create AVD
    print_status "Creating AVD with system image..."
    echo "no" | avdmanager create avd \
        -n "$avd_name" \
        -k "system-images;android-${api_level};google_apis;x86_64" \
        -d "pixel_xl" \
        --force
        
    print_success "AVD $avd_name created successfully"
}

# Start emulator
start_emulator() {
    local api_level=$1
    local avd_name="test_avd_${api_level}"
    
    print_status "Starting emulator for API $api_level..."
    
    # Check if emulator is already running
    if adb devices | grep -q "emulator"; then
        print_warning "Emulator already running. Stopping existing emulator..."
        adb emu kill
        sleep 5
    fi
    
    # Start emulator (background)
    emulator -avd "$avd_name" \
        -no-window \
        -gpu swiftshader_indirect \
        -noaudio \
        -no-boot-anim \
        -camera-back none \
        -memory 3072 \
        -partition-size 4096 &
    
    local emulator_pid=$!
    print_status "Emulator started with PID: $emulator_pid"
    
    # Wait for emulator boot completion
    print_status "Waiting for emulator to boot..."
    adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed | tr -d '\r') ]]; do sleep 1; done'
    
    # Unlock screen
    adb shell input keyevent 82
    
    # Set resolution
    adb shell wm size 1080x1920
    adb shell wm density 440
    
    print_success "Emulator API $api_level is ready!"
    return $emulator_pid
}

# Run tests
run_tests() {
    local api_level=$1
    
    print_status "Running Dropshots tests for API $api_level..."
    
    # Execute tests
    ./gradlew :app:connectedDebugAndroidTest \
        --no-daemon \
        --no-build-cache \
        -Pandroid.testInstrumentationRunnerArguments.class=com.skydoves.cloudydemo.MainTest \
        || {
            print_error "Tests failed for API $api_level"
            return 1
        }
    
    # Collect screenshots
    collect_screenshots $api_level
    
    print_success "Tests completed successfully for API $api_level"
}

# Collect screenshots
collect_screenshots() {
    local api_level=$1
    local output_dir="screenshots/api-$api_level"
    
    print_status "Collecting screenshots for API $api_level..."
    
    mkdir -p "$output_dir"
    
    # Collect screenshots from multiple locations
    adb pull /sdcard/dropshots/ "$output_dir/" 2>/dev/null || true
    adb pull /storage/emulated/0/dropshots/ "$output_dir/" 2>/dev/null || true
    
    # Also collect from build outputs
    find ./app/build/outputs -name "*.png" -type f -exec cp {} "$output_dir/" \; 2>/dev/null || true
    
    # Clean up filenames
    cd "$output_dir"
    for file in *.png 2>/dev/null; do
        if [[ -f "$file" && "$file" != *"api$api_level"* ]]; then
            base_name="${file%.png}"
            mv "$file" "${base_name}_api${api_level}.png" 2>/dev/null || true
        fi
    done
    cd - > /dev/null
    
    local count=$(find "$output_dir" -name "*.png" 2>/dev/null | wc -l)
    print_success "Collected $count screenshots for API $api_level"
}

# Clean up emulator
cleanup_emulator() {
    print_status "Cleaning up emulator..."
    adb emu kill 2>/dev/null || true
    sleep 2
}

# Main execution logic
main() {
    print_status "Cloudy Library Screenshot Testing"
    print_status "Testing Native RenderScript Toolkit on real Android emulators"
    echo ""
    
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
    
    # Execute based on selected API level
    if [[ "$SELECTED_API" == "all" ]]; then
        print_status "Running tests for all API levels: ${API_LEVELS[*]}"
        
        for api in "${API_LEVELS[@]}"; do
            echo ""
            print_status "========== API Level $api =========="
            
            check_emulator $api
            start_emulator $api
            emulator_pid=$!
            
            # Run tests
            if run_tests $api; then
                print_success "API $api tests completed successfully"
            else
                print_error "API $api tests failed"
            fi
            
            # Clean up emulator
            cleanup_emulator
            
            # Wait before next test
            sleep 5
        done
        
    else
        # Run specific API level only
        if [[ " ${API_LEVELS[*]} " =~ " ${SELECTED_API} " ]]; then
            print_status "Running tests for API level $SELECTED_API"
            
            check_emulator $SELECTED_API
            start_emulator $SELECTED_API
            run_tests $SELECTED_API
            cleanup_emulator
            
        else
            print_error "Invalid API level: $SELECTED_API"
            print_status "Available API levels: ${API_LEVELS[*]}"
            exit 1
        fi
    fi
    
    # Result summary
    echo ""
    print_success "🎉 Screenshot testing completed!"
    print_status "📁 Screenshots saved in: ./screenshots/"
    
    # Display number of collected screenshots
    total_screenshots=$(find ./screenshots -name "*.png" 2>/dev/null | wc -l)
    print_status "📊 Total screenshots captured: $total_screenshots"
    
    if [[ $total_screenshots -gt 0 ]]; then
        print_status "📋 Screenshot breakdown:"
        for api in "${API_LEVELS[@]}"; do
            local count=$(find "./screenshots/api-$api" -name "*.png" 2>/dev/null | wc -l)
            if [[ $count -gt 0 ]]; then
                print_status "   API $api: $count screenshots"
            fi
        done
    fi
}

# Ctrl+C handler
trap 'print_warning "Script interrupted. Cleaning up..."; cleanup_emulator; exit 1' INT

# Display usage
if [[ "$1" == "-h" || "$1" == "--help" ]]; then
    echo "Usage: $0 [API_LEVEL|all]"
    echo ""
    echo "Examples:"
    echo "  $0           # Run tests for all API levels (27, 30, 33)"
    echo "  $0 all       # Run tests for all API levels"
    echo "  $0 30        # Run tests for API level 30 only"
    echo ""
    echo "Available API levels: ${API_LEVELS[*]}"
    echo ""
    echo "Prerequisites:"
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
    exit 0
fi

# Execute main function
main
