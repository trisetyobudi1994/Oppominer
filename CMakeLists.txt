cmake_minimum_required(VERSION 3.22.1)

project("oppominer")

# Compiler optimization flags for ARM64-v8a
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -flto -fomit-frame-pointer")

# Check if target is ARM64
if (ANDROID_ABI STREQUAL "arm64-v8a")
    add_definitions(-D__ARM_ARCH_8A__)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a+crypto -funroll-loops")
endif()

add_library(
    native-miner
    SHARED
    native-miner.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    native-miner
    ${log-lib}
)
